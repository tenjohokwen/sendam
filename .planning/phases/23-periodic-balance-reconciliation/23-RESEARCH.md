# Phase 23: Periodic Balance Reconciliation - Research

**Researched:** 2026-03-17
**Domain:** Spring @Scheduled, Nexah /smscredit, deviation alert persistence, config properties
**Confidence:** HIGH

---

## Summary

Phase 23 adds a scheduled reconciliation job that polls Nexah's `/smscredit` endpoint at a configurable interval and records a `BALANCE` deviation alert when Nexah's reported credit does not match Sendam's tracked `PlatformCreditBalance`. The phase involves:

1. A new `@ConfigurationProperties` record for `sendam.reconciliation.*` bound from a new `@Configuration` class.
2. A new scheduled service (`BalanceReconciliationJob`) using `@Scheduled(fixedDelayString = ...)` driven by the configured interval.
3. A new `BalanceDeviationAlert` entity (new table `V15`) because `SegmentDeviationAlert` is structurally incompatible — its `send_request_id_fk` column is `NOT NULL` with a FK to `send_request`, making it impossible to use for balance alerts that have no associated send request.
4. `BALANCE` added to `DeviationAlertType` enum.
5. A new `BALANCE_DEVIATION` audit event type added to `AuditEventType` enum.
6. The requirements say the alert status is `OPEN` — but `EntityStatus` only has `ACTIVE/INACTIVE/DELETED`. A new `AlertStatus` enum (`OPEN`, `ACKNOWLEDGED`, `CLOSED`) must be introduced, or `EntityStatus.ACTIVE` is used with "status OPEN" interpreted as the initial active state. See decision note below.

**Primary recommendation:** Create a separate `BalanceDeviationAlert` entity (new table `V15`), add `BALANCE` to `DeviationAlertType`, add a `@ConfigurationProperties` record bound by a billing config class, and implement the job as a `@Service` with `@Scheduled(fixedDelayString)`. The `EntityStatus.ACTIVE` enum value maps to the "OPEN" status from the requirements — no new enum file is needed.

---

## Standard Stack

### Core
| Library/API | Version | Purpose | Source |
|---|---|---|---|
| `spring-context` `@Scheduled` | Spring Boot 3.5.11 | Periodic job execution | Already in use in project |
| `spring-boot` `@ConfigurationProperties` | Spring Boot 3.5.11 | Type-safe config binding | Used by `NexahProperties`, `EmailProperties` |
| `RestTemplate` | Spring Boot 3.5.11 | Nexah `/smscredit` HTTP call | `NexahClient` already uses it |
| `io.hypersistence.utils` `@Tsid` | Already in project | PK generation for new alert entity | `BaseEntity` |

### Supporting
| Library | Purpose | When to Use |
|---|---|---|
| `resilience4j` circuit breaker | Guard HTTP calls to Nexah | Already wraps `sendSms`; reconciliation job should handle `RestClientException` defensively without circuit breaker (this is a background probe, not a user-facing call) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|---|---|---|
| New `BalanceDeviationAlert` entity | Reuse `SegmentDeviationAlert` | `SegmentDeviationAlert.send_request_id_fk` is NOT NULL FK to `send_request` — cannot store balance alerts; reuse is structurally impossible |
| `fixedDelayString` with minutes math | `@Scheduled(fixedRateString)` | `fixedDelay` (gap between end and next start) is safer for external HTTP calls; avoids overlapping runs if Nexah is slow |

**Installation:** No new dependencies required. All needed libraries are already on the classpath.

---

## Architecture Patterns

### Recommended Project Structure

New files for Phase 23 all live inside the existing `gateway/billing` module:

```
gateway/billing/
├── contract/
│   ├── DeviationAlertType.java          # ADD BALANCE constant
│   ├── ReconciliationProperties.java    # NEW @ConfigurationProperties
├── repo/
│   ├── BalanceDeviationAlert.java       # NEW entity
│   ├── BalanceDeviationAlertRepository.java  # NEW minimal stub
├── service/
│   ├── BalanceReconciliationJob.java    # NEW @Service with @Scheduled
├── config/                             # NEW directory
│   ├── BillingConfig.java              # NEW @Configuration + @EnableConfigurationProperties

gateway/provider/nexah/infrastructure/
└── NexahClient.java                    # ADD fetchCreditBalance() method returning long

gateway/audit/contract/
└── AuditEventType.java                 # ADD BALANCE_DEVIATION constant
```

Application YAML changes:
```
src/main/resources/application.yaml    # ADD sendam.reconciliation.interval-minutes: 15
src/main/resources/db/migration/
└── V15__balance_deviation_alert.sql   # NEW Flyway migration
```

### Pattern 1: @ConfigurationProperties record in contract layer

The project uses `record`-based `@ConfigurationProperties` for Nexah (`NexahProperties`) and a class-based variant for `EmailProperties`. For `sendam.*` namespace, use the record form:

```java
// Source: NexahProperties.java (existing codebase pattern)
@ConfigurationProperties(prefix = "sendam.reconciliation")
public record ReconciliationProperties(
    int intervalMinutes   // maps to sendam.reconciliation.interval-minutes
) {}
```

Registered via `@EnableConfigurationProperties(ReconciliationProperties.class)` on a new `@Configuration` class `BillingConfig`. The billing module has no existing config class (the directory is empty).

### Pattern 2: @EnableConfigurationProperties on a dedicated @Configuration

Nexah uses `ClientConfig` (in `sms/config`):
```java
// Source: ClientConfig.java (existing)
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NexahProperties.class)
public class ClientConfig {}
```

The new `BillingConfig` follows the same pattern:
```java
@Configuration
@EnableConfigurationProperties(ReconciliationProperties.class)
public class BillingConfig {}
```

`@EnableScheduling` is already present on `ClientConfig` (in `sms/config`) and `AsyncConfig` (in `email/config`). Spring only needs one `@EnableScheduling` in the application context — do NOT add it to `BillingConfig`. The existing one from `ClientConfig` covers the whole application.

### Pattern 3: fixedDelayString for configurable interval

Two existing examples use string-based delay configuration:
- `EmailRetryScheduler`: `@Scheduled(fixedDelayString = "${email.retry.interval-ms:60000}")`
- `SmsPurgeService`: `@Scheduled(cron = "0 0 2 * * *")` (hardcoded cron)

The requirement says interval in minutes. Use `fixedDelayString` with milliseconds conversion:

```java
// Source: EmailRetryScheduler.java pattern (existing codebase)
@Scheduled(fixedDelayString = "#{${sendam.reconciliation.interval-minutes:15} * 60000}")
public void reconcile() { ... }
```

The Spring SpEL expression `#{${prop} * 60000}` evaluates the property then multiplies. This is the standard Spring pattern for minute-to-millisecond conversion in `fixedDelayString`.

Alternative approach using `ReconciliationProperties` bean directly is also valid but requires a separate `@Bean` TaskScheduler configuration. The SpEL approach is simpler and consistent with the project.

### Pattern 4: NexahClient.fetchCreditBalance()

`NexahClient.checkAvailability()` already calls `/smscredit` but only returns a boolean. A new method `fetchCreditBalance()` is needed that returns the `credit` integer from the response.

The `/smscredit` response structure (from `nexahApi.md`, HIGH confidence):
```json
{
  "responsecode": 1,
  "credit": 4,
  "balance": [...]
}
```

The top-level `credit` field is the total credits remaining. This is the authoritative number to compare against `PlatformCreditBalance.balance`.

```java
// Pattern: same RestTemplate + credential-in-body auth as checkAvailability()
public long fetchCreditBalance() {
    final String url = properties.baseUrl() + "/smscredit";
    final Map<String, String> body = Map.of(
        "user", properties.user(),
        "password", properties.password()
    );
    Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
    if (response == null || !Integer.valueOf(1).equals(response.get("responsecode"))) {
        throw new ProviderUnavailableException("Nexah /smscredit call failed");
    }
    Object credit = response.get("credit");
    return ((Number) credit).longValue();
}
```

### Pattern 5: Reading the Sendam-tracked balance

`PlatformCreditService.getBalance()` already provides a non-locking read:

```java
// Source: PlatformCreditService.java (existing)
@Transactional(readOnly = true)
public PlatformBalanceResponse getBalance() { ... }
```

`PlatformBalanceResponse` exposes `balance()` (a `long`). The reconciliation job can call `platformCreditService.getBalance().balance()` — no new repository method needed.

### Pattern 6: BalanceDeviationAlert entity design

The requirements say: Nexah-reported balance, Sendam-tracked balance, delta, timestamp, status OPEN.

The project's `EntityStatus.ACTIVE` maps cleanly to "OPEN" semantics (Phase 24 will add management operations including status transitions). The V14 schema used `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` — the same applies to V15.

A minimal entity schema:
```sql
CREATE TABLE main.balance_deviation_alert (
    id                  BIGINT       PRIMARY KEY,
    nexah_balance       BIGINT       NOT NULL,
    sendam_balance      BIGINT       NOT NULL,
    delta               BIGINT       NOT NULL,   -- nexah_balance - sendam_balance
    alert_type          VARCHAR(30)  NOT NULL,   -- always BALANCE
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMPTZ,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMPTZ,
    request_id          VARCHAR(255),
    session_id          TEXT
);
```

The entity extends `AbstractAuditingEntity` (provides id via `@Tsid`, audit columns, status). No JSONB needed. No FK to send_request.

### Anti-Patterns to Avoid

- **Do not reuse `SegmentDeviationAlert`:** The `send_request_id_fk` column is `NOT NULL` with a FK constraint. Adding `BALANCE` to `SegmentDeviationAlert` would require making that FK nullable (schema change) and making 8+ fields irrelevant. A dedicated table is cleaner.
- **Do not add `@EnableScheduling` to `BillingConfig`:** It is already present in `ClientConfig`. Duplicate annotation is harmless but noise.
- **Do not call `PlatformCreditBalanceRepository.findForUpdate()` in the job:** That method acquires a pessimistic write lock. The reconciliation job only needs a read-only snapshot — use `PlatformCreditService.getBalance()` which calls `findBalance()` (no lock).
- **Do not call `NexahClient.sendSms()` from the job:** Wrong method. The circuit breaker wraps `sendSms()` only; `fetchCreditBalance()` is an independent read-only probe.
- **Do not hardcode interval in milliseconds:** Requirement BALREC-04 explicitly requires configurability via `sendam.reconciliation.interval-minutes`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Configurable properties binding | Manual `@Value` injection | `@ConfigurationProperties` record | Already the project pattern; type-safe; testable |
| HTTP call to `/smscredit` | New RestTemplate setup | Add method to existing `NexahClient` | NexahClient already owns the RestTemplate, credentials, and base URL |
| Reading Sendam tracked balance | Direct repo call | `PlatformCreditService.getBalance()` | Service already has the non-locking read query; consistent API |
| PK generation | Custom UUID or sequence | `@Tsid` via `AbstractAuditingEntity` inheritance | All tables use TSID; confirmed in 22-01 decision |

**Key insight:** All infrastructure already exists. Phase 23 is glue: a new config record, a new entity, a new scheduled job. The heavy lifting is in existing services.

---

## Common Pitfalls

### Pitfall 1: smscredit `credit` vs `balance[].credit` confusion
**What goes wrong:** Developer reads `balance[0].credit` (country-level credit) instead of the top-level `credit` field.
**Why it happens:** The `/smscredit` response has both a top-level `credit` and a `balance` array with per-country `credit` fields.
**How to avoid:** Always use top-level `credit` from the response. This is the total credit units remaining — the same number Sendam tracks as `PlatformCreditBalance.balance`.
**Warning signs:** Delta is always zero or wildly wrong in tests.

### Pitfall 2: `@EnableScheduling` declared twice
**What goes wrong:** Works, but produces startup log noise and potential confusion about which config owns scheduling.
**Why it happens:** Developer adds `@EnableScheduling` to `BillingConfig` not knowing `ClientConfig` already declares it.
**How to avoid:** Check that `@EnableScheduling` is already on `ClientConfig` (confirmed in research). `BillingConfig` needs only `@EnableConfigurationProperties`.

### Pitfall 3: SpEL expression in `fixedDelayString` property syntax
**What goes wrong:** `@Scheduled(fixedDelayString = "${sendam.reconciliation.interval-minutes:15} * 60000")` — this is a property placeholder, not a SpEL expression. String multiplication is not performed.
**Why it happens:** Mixing `${}` and SpEL `#{}` syntax.
**How to avoid:** Use SpEL: `@Scheduled(fixedDelayString = "#{${sendam.reconciliation.interval-minutes:15} * 60000}")`. The outer `#{}` triggers SpEL evaluation of the inner numeric expression.
**Alternative:** Store the property in seconds or milliseconds directly in YAML: `interval-ms: 900000` and use `fixedDelayString = "${sendam.reconciliation.interval-ms:900000}"` — avoids SpEL entirely and matches `EmailRetryScheduler` pattern. This alternative is preferred for simplicity.

### Pitfall 4: Job runs inside its own `@Transactional` method but Nexah call times out holding a DB connection
**What goes wrong:** The reconciliation job opens a transaction, calls Nexah (potentially slow), then writes the alert — holding a connection for the duration of the HTTP call.
**Why it happens:** Single `@Transactional` method wrapping both the HTTP call and the DB write.
**How to avoid:** Keep the Nexah HTTP call outside the transaction. Fetch nexahBalance first (no transaction), then fetch sendamBalance, compare, and only open a transaction for the alert write if needed.

### Pitfall 5: Missing `@EnableConfigurationProperties` causes `ReconciliationProperties` not bound
**What goes wrong:** Application starts but `intervalMinutes` is 0 or throws `UnsatisfiedDependencyException`.
**Why it happens:** `@ConfigurationProperties` records are not auto-detected; they must be registered via `@EnableConfigurationProperties` on a `@Configuration` class.
**How to avoid:** Create `BillingConfig.java` with `@EnableConfigurationProperties(ReconciliationProperties.class)`.

---

## Code Examples

### fixedDelayString with millisecond property (preferred, matches EmailRetryScheduler pattern)

```java
// Source: EmailRetryScheduler.java pattern — use ms property, not SpEL multiplication
// Add to application.yaml: sendam.reconciliation.interval-ms: 900000
@Scheduled(fixedDelayString = "${sendam.reconciliation.interval-ms:900000}")
public void reconcile() { ... }
```

However, BALREC-04 specifically names `sendam.reconciliation.interval-minutes`. To satisfy the requirement literally while avoiding SpEL:

```java
// Option A: Store in minutes as required; convert in @Scheduled via SpEL
@Scheduled(fixedDelayString = "#{${sendam.reconciliation.interval-minutes:15} * 60000L}")
```

```java
// Option B: Inject the property bean and compute in a @PostConstruct TaskScheduler setup
// (more complex; not recommended for a single job)
```

**Recommendation:** Option A (SpEL) satisfies BALREC-04 naming exactly and is a standard Spring pattern.

### @ConfigurationProperties record (sendam.* prefix convention)

```java
// Source: NexahProperties.java pattern (existing codebase)
// file: gateway/billing/contract/ReconciliationProperties.java
@ConfigurationProperties(prefix = "sendam.reconciliation")
public record ReconciliationProperties(int intervalMinutes) {}
```

### Reading Sendam balance (no lock)

```java
// Source: PlatformCreditService.java — getBalance() is @Transactional(readOnly=true)
long sendamBalance = platformCreditService.getBalance().balance();
```

### Reading Nexah balance

```java
// To be added to NexahClient.java — same credential-in-body pattern as checkAvailability()
@SuppressWarnings("unchecked")
public long fetchCreditBalance() {
    final String url = properties.baseUrl() + "/smscredit";
    Map<String, String> body = Map.of("user", properties.user(), "password", properties.password());
    Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
    if (response == null) throw new ProviderUnavailableException("Nexah returned null for /smscredit");
    Object rc = response.get("responsecode");
    if (!Integer.valueOf(1).equals(rc) && !"1".equals(String.valueOf(rc))) {
        throw new ProviderUnavailableException("Nexah /smscredit non-success: " + rc);
    }
    return ((Number) response.get("credit")).longValue();
}
```

### BalanceReconciliationJob skeleton

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceReconciliationJob {

    private final NexahClient nexahClient;
    private final PlatformCreditService platformCreditService;
    private final BalanceDeviationAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "#{${sendam.reconciliation.interval-minutes:15} * 60000L}")
    public void reconcile() {
        // 1. Fetch Nexah balance (outside transaction — no DB connection held during HTTP)
        long nexahBalance;
        try {
            nexahBalance = nexahClient.fetchCreditBalance();
        } catch (Exception e) {
            log.warn("Balance reconciliation skipped: Nexah unavailable: {}", e.getMessage());
            return;  // skip this cycle; try again next interval
        }

        // 2. Read Sendam tracked balance (read-only transaction, brief)
        long sendamBalance = platformCreditService.getBalance().balance();

        // 3. Compare
        long delta = nexahBalance - sendamBalance;
        if (delta == 0) {
            log.debug("Balance reconciliation OK: nexah={}, sendam={}", nexahBalance, sendamBalance);
            return;
        }

        // 4. Record BALANCE deviation alert
        recordAlert(nexahBalance, sendamBalance, delta);
    }

    @Transactional
    protected void recordAlert(long nexahBalance, long sendamBalance, long delta) {
        BalanceDeviationAlert alert = BalanceDeviationAlert.builder()
            .nexahBalance(nexahBalance)
            .sendamBalance(sendamBalance)
            .delta(delta)
            .alertType(DeviationAlertType.BALANCE)
            .status(EntityStatus.ACTIVE)   // "OPEN" in requirement terminology
            .build();
        alertRepository.save(alert);

        eventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.BALANCE_DEVIATION,
            null,   // no clientId for platform-level event
            "system",
            "Balance deviation: nexah=" + nexahBalance + ", sendam=" + sendamBalance + ", delta=" + delta
        ));

        log.warn("BALANCE deviation alert created: nexah={}, sendam={}, delta={}", nexahBalance, sendamBalance, delta);
    }
}
```

Note: `@Transactional` on `protected` method within the same class does NOT proxy through Spring AOP. The `recordAlert` call must be either in a separate bean, or the `@Transactional` annotation is placed on the `reconcile()` method only for the write segment. The cleaner approach: inject a `BalanceDeviationAlertService` from a separate service bean that owns the `@Transactional` write, consistent with `SegmentDeviationService` pattern.

**Revised structure:** Introduce a thin `BalanceDeviationAlertService` (mirrors `SegmentDeviationService`) that owns the `@Transactional createAlert()`. The job calls the service — same pattern as Phase 22.

---

## Flyway Migration (V15)

Next migration is `V15__balance_deviation_alert.sql`. V14 was `V14__segment_deviation_alert.sql`.

```sql
-- Phase 23: Balance deviation alert table
-- Records periodic balance reconciliation mismatches between Nexah-reported
-- credits and Sendam-tracked platform balance.
-- PK is BIGINT generated by TSID — no sequence DDL needed.

CREATE TABLE main.balance_deviation_alert (
    id                  BIGINT       PRIMARY KEY,
    alert_type          VARCHAR(30)  NOT NULL DEFAULT 'BALANCE',
    nexah_balance       BIGINT       NOT NULL,
    sendam_balance      BIGINT       NOT NULL,
    delta               BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMPTZ,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMPTZ,
    request_id          VARCHAR(255),
    session_id          TEXT
);

CREATE INDEX idx_balance_deviation_alert_status
    ON main.balance_deviation_alert(status);

CREATE INDEX idx_balance_deviation_alert_created_date
    ON main.balance_deviation_alert(created_date);
```

---

## Status "OPEN" Decision

BALREC-03 says "status OPEN". `EntityStatus` has `ACTIVE/INACTIVE/DELETED` — no `OPEN`.

**Options:**
1. Use `EntityStatus.ACTIVE` to mean OPEN (Phase 24 will manage status transitions — it can acknowledge/close using INACTIVE or DELETED if needed, or introduce a new enum then).
2. Create a new `AlertStatus` enum (`OPEN`, `ACKNOWLEDGED`, `CLOSED`) now — anticipating Phase 24's management needs.

**Recommendation:** Option 2 is architecturally cleaner but requires deciding this now. The `SegmentDeviationAlert` already uses `EntityStatus` (ACTIVE) — if `BalanceDeviationAlert` introduces a new `AlertStatus`, the two alert types will use different status systems, which creates inconsistency that Phase 24 must reconcile.

**Pragmatic recommendation for the planner:** Use `EntityStatus.ACTIVE` = "OPEN" for V15. The column stores the string "ACTIVE". Phase 24 can introduce a proper `AlertStatus` enum and migrate both alert tables if needed. Document this as a known design debt.

---

## DomainAuditEvent clientId for platform-level events

`DomainAuditEvent` takes a `clientId`. For `SegmentDeviationService`, this is the client whose send request caused the deviation. For balance reconciliation, there is no client — it is a platform-level event. Check the `DomainAuditEvent` constructor signature to confirm whether `clientId` is nullable.

Based on the existing pattern in `SegmentDeviationService` (clientId is always non-null there), the constructor may require a non-null clientId. The planner should verify and either use a sentinel value (e.g., 0L) or add a nullable-clientId constructor variant. This is a LOW confidence open question.

---

## How Many Plans

**Recommendation: 2 plans.**

**Plan 23-01 (infrastructure):**
- V15 Flyway migration
- `BalanceDeviationAlert` entity
- `BalanceDeviationAlertRepository` stub
- Add `BALANCE` to `DeviationAlertType`
- Add `BALANCE_DEVIATION` to `AuditEventType`
- `ReconciliationProperties` config record
- `BillingConfig` configuration class
- `NexahClient.fetchCreditBalance()` method
- Add `sendam.reconciliation.interval-minutes: 15` to application.yaml

**Plan 23-02 (job + service + tests):**
- `BalanceDeviationAlertService` with `createAlert()` (mirrors SegmentDeviationService)
- `BalanceReconciliationJob` scheduled service
- Unit tests for `BalanceReconciliationJob` (happy path, nexah unavailable skip, exact match skip, mismatch triggers alert)
- Unit tests for `BalanceDeviationAlertService`

This split mirrors the Phase 22 pattern (01=infrastructure, 02=orchestration logic, 03=tests) compressed into 2 plans because the domain is simpler — there are no multi-scenario decision trees.

---

## Open Questions

1. **`DomainAuditEvent` clientId nullability**
   - What we know: `DomainAuditEvent` constructor is called with a clientId in all existing usages
   - What's unclear: Whether null is accepted or whether a sentinel value is needed for platform-level events
   - Recommendation: Plan 01 should read `DomainAuditEvent` and `AuditEventService` to confirm; if null is not supported, use `0L` as the platform sentinel (consistent with singleton platform balance row using id=1)

2. **`DomainAuditEvent` source in `SegmentDeviationService`** — clientId is the SMS client's ID. For balance reconciliation there is no client. Confirm nullability before Plan 02 implementation.

3. **Threshold tolerance** — BALREC-02 says "differs from." Does delta=1 credit qualify? No tolerance is specified in the requirements. Assume zero tolerance (any delta != 0 triggers alert). Planner should confirm with requirements.

---

## Sources

### Primary (HIGH confidence)
- Codebase direct read: `NexahClient.java` — `checkAvailability()` shows exact `/smscredit` call pattern and response field access
- Codebase direct read: `requirements/nexahApi.md` — Official Nexah API spec; `/smscredit` response structure with top-level `credit` field
- Codebase direct read: `SegmentDeviationAlert.java`, `V14__segment_deviation_alert.sql` — existing alert entity structure; confirms `send_request_id_fk NOT NULL`
- Codebase direct read: `PlatformCreditService.java` — `getBalance()` read-only method; `PlatformCreditBalance.balance` field
- Codebase direct read: `EmailRetryScheduler.java` — `fixedDelayString` with property placeholder pattern
- Codebase direct read: `ClientConfig.java` — `@EnableScheduling` + `@EnableConfigurationProperties` pattern
- Codebase direct read: `NexahProperties.java` — `@ConfigurationProperties(prefix = "nexah")` record pattern
- Codebase direct read: `EntityStatus.java` — only `ACTIVE/INACTIVE/DELETED`; no `OPEN`
- Codebase direct read: `AuditEventType.java` — existing audit types; `BALANCE_DEVIATION` not yet present
- Codebase direct read: `DeviationAlertType.java` — only `SEGMENT` and `PLATFORM_FREEZE`; `BALANCE` not yet present

### Secondary (MEDIUM confidence)
- Spring `@Scheduled` SpEL multiplication pattern for minutes-to-ms conversion: standard documented Spring feature, verified against project's existing Spring Boot 3.5.11 stack

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all libraries already in use; no new dependencies
- Architecture: HIGH — all patterns are from existing codebase
- New entity structure: HIGH — derived from V14 schema + requirements
- Pitfalls: HIGH — derived from direct code inspection
- Status "OPEN" semantics: MEDIUM — interpretation of requirements against existing enum; documented as open question

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable Spring Boot project; low churn risk)
