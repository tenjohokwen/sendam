# Phase 10: System Health (Admin) - Research

**Researched:** 2026-03-11
**Domain:** Resilience4j circuit breaker state, webhook_delivery aggregation, send_request_recipient aggregation, admin endpoint pattern
**Confidence:** HIGH

## Summary

This phase builds three read-only admin health endpoints. All data sources are already in the
codebase: the Resilience4j `CircuitBreakerRegistry` bean is injectable (already used in
`SmsService`), and the data tables (`webhook_delivery`, `send_request_recipient`) are established.

The standard implementation pattern (established in phases 8 and 9) is:
- `gateway/health/` module with `contract/`, `repo/`, `service/`, `api/` sub-packages
- `Repository<Object, Long>` for pure aggregation (no entity binding needed)
- Native `@Query` on the aggregation repo, projection interfaces in `contract/`
- Admin controller at `/api/admin/health/**` with `@PreAuthorize("hasRole('ADMIN')")`
- New `ADMIN_HEALTH` constant in `AppEndpoints` — this is entry 10, which forces the switch from `Map.of()` to `Map.ofEntries()`
- No Flyway migration needed — this phase reads existing tables, no DDL changes

**Primary recommendation:** Three separate endpoints under `/api/admin/health`. HLTH-01 reads in-memory circuit breaker state (no SQL). HLTH-02 queries `webhook_delivery` by `attempt_status`. HLTH-03 queries `send_request_recipient` and `send_request` by `send_status`. One controller, one service, one repo for HLTH-02 and HLTH-03; circuit breaker injection directly into the service.

---

## Standard Stack

### Core (all already on classpath)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.github.resilience4j:resilience4j-circuitbreaker` | 2.2.0 | `CircuitBreakerRegistry` bean, `CircuitBreaker.State`, `CircuitBreaker.Metrics` | Already in pom via spring-cloud-starter-circuitbreaker-resilience4j |
| Spring Data JPA | (Boot 3.5.11) | `Repository<Object, Long>` aggregation repos | Project standard; no entity binding needed |
| Spring Web | (Boot 3.5.11) | `@RestController`, `ResponseEntity` | Project standard |
| Lombok | (Boot 3.5.11) | `@RequiredArgsConstructor`, `@Slf4j` | Project standard |

### No New Dependencies

Phase 10 requires zero new pom.xml entries.

**Installation:** none

---

## Architecture Patterns

### Recommended Package Structure

```
gateway/health/
├── api/
│   └── AdminHealthResource.java        # @RestController at /api/admin/health
├── contract/
│   ├── CircuitBreakerHealthResponse.java  # record
│   ├── WebhookHealthResponse.java         # record
│   ├── ProviderStatsResponse.java         # record
│   └── WebhookStatsRow.java               # projection interface
│   └── ProviderStatsRow.java              # projection interface
├── repo/
│   └── HealthRepository.java           # Repository<Object, Long>, two native @Query
└── service/
    └── HealthService.java              # injects CircuitBreakerRegistry + HealthRepository
```

This mirrors `gateway/analytics/` and `gateway/spend/` exactly.

### Pattern 1: CircuitBreakerRegistry Injection (HLTH-01)

`CircuitBreakerRegistry` is a Spring-managed bean provided by Resilience4j auto-configuration.
It is already injected into `SmsService` and is available for injection anywhere.

```java
// Source: SmsService.java line 63, 134-135 — verified in codebase
private final CircuitBreakerRegistry circuitBreakerRegistry;

// To read state:
CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
CircuitBreaker.State state = cb.getState();  // returns CLOSED, OPEN, or HALF_OPEN
```

The circuit breaker name `"nexah"` is the key used in `@CircuitBreaker(name = "nexah")` on
`NexahClient.sendSms()` and in `application.yaml` under `resilience4j.circuitbreaker.instances.nexah`.

`CircuitBreaker.Metrics` is available via `cb.getMetrics()` and exposes:
- `getFailureRate()` — float
- `getNumberOfBufferedCalls()` — int
- `getNumberOfFailedCalls()` — int
- `getNumberOfSuccessfulCalls()` — int
- `getNumberOfNotPermittedCalls()` — long (calls rejected while OPEN)

`CircuitBreaker.State` enum values (Resilience4j 2.2.0): `CLOSED`, `OPEN`, `HALF_OPEN`,
`DISABLED`, `METRICS_ONLY`, `FORCED_OPEN`. The three states relevant to HLTH-01 are
`CLOSED`, `OPEN`, `HALF_OPEN`.

The `getState()` call is in-memory only — no network call, no database query.

### Pattern 2: Aggregation Repository for HLTH-02 (webhook_delivery)

Source table: `main.webhook_delivery`, column `attempt_status` (VARCHAR, values from
`WebhookDeliveryStatus` enum: `PENDING`, `DELIVERED`, `FAILED`, `EXHAUSTED`).

HLTH-02 requires: total attempts, failure count, EXHAUSTED count.

```sql
-- Aggregate across all rows in webhook_delivery
SELECT
    COUNT(*)                                                   AS total_attempts,
    COUNT(CASE WHEN d.attempt_status = 'FAILED'     THEN 1 END) AS failure_count,
    COUNT(CASE WHEN d.attempt_status = 'EXHAUSTED'  THEN 1 END) AS exhausted_count
FROM main.webhook_delivery d
```

No filter parameters needed for a "current health snapshot". This is a single-row aggregate.
Projection interface `WebhookStatsRow` with `getTotalAttempts()`, `getFailureCount()`,
`getExhaustedCount()` (all `long`).

### Pattern 3: Aggregation Repository for HLTH-03 (provider send stats)

HLTH-03 requires: SMS submissions sent to Nexah, DR callbacks received, failure rate.

**"SMS submissions sent to Nexah"** = rows in `send_request_recipient` where
`send_status IN ('SUBMITTED', 'COMPLETED', 'FAILED', 'FINALIZED', 'FAIL_FINALIZED')` —
i.e., rows that were successfully handed to Nexah (advanced past ACCEPTED).
Alternatively, count all `send_request` rows with `send_status != 'ACCEPTED'` and
`send_status != 'CANCELLED'`. The recipient table is the correct granularity (per SMS, not
per batch).

**"DR callbacks received"** = rows in `send_request_recipient` where
`send_status IN ('COMPLETED', 'FAILED', 'FINALIZED', 'FAIL_FINALIZED')` — these are
recipients that received a delivery report from Nexah (their status was advanced by
`DrCallbackService`). `SUBMITTED` means dispatched but no DR yet.

**"Failure rate"** = rows with `send_status = 'FAIL_FINALIZED'` or `send_status = 'FAILED'`
divided by total DR-received rows, expressed as a percentage. Guard at `dr_received == 0`
(return 0.0).

```sql
SELECT
    COUNT(CASE WHEN r.send_status IN ('SUBMITTED','COMPLETED','FAILED','FINALIZED','FAIL_FINALIZED')
               THEN 1 END)                               AS total_submitted,
    COUNT(CASE WHEN r.send_status IN ('COMPLETED','FAILED','FINALIZED','FAIL_FINALIZED')
               THEN 1 END)                               AS dr_received,
    COUNT(CASE WHEN r.send_status IN ('FAILED','FAIL_FINALIZED')
               THEN 1 END)                               AS failed_count
FROM main.send_request_recipient r
```

Failure rate computed in service layer: `drReceived == 0 ? 0.0 : (double) failedCount / drReceived * 100.0`.

Note: `FAIL_FINALIZED` is only on `send_request` (parent), not on `send_request_recipient`.
The recipient-level terminal failure status is `FAILED`. `FINALIZED` is also only on the
parent. For the recipient table, the meaningful DR-received statuses are `COMPLETED`
(DELIVRD) and `FAILED` (anything non-DELIVRD). So:

- `total_submitted` = `COUNT` where `send_status != 'ACCEPTED' AND send_status != 'CANCELLED'`
- `dr_received` = `COUNT` where `send_status IN ('COMPLETED', 'FAILED')`
- `failed_count` = `COUNT` where `send_status = 'FAILED'`

### Pattern 4: Three Separate Endpoints vs One Combined

**Use three separate GET endpoints under a single controller.** This matches HLTH-01/02/03
as distinct concerns with distinct data sources. Prior phases 8 and 9 similarly split logically
distinct data into separate endpoints under one controller class.

```
GET /api/admin/health/circuit-breaker   → HLTH-01
GET /api/admin/health/webhook-stats     → HLTH-02
GET /api/admin/health/provider-stats    → HLTH-03
```

### Pattern 5: AppEndpoints — Mandatory Map.ofEntries() Switch

The current `SECURED_MAPPINGS = Map.of(...)` has 9 entries. Java's `Map.of()` overloads cap
at 10 key-value pairs. Adding one more entry (ADMIN_HEALTH) brings the count to 10, which
is at the limit. Per the stated prior decision: **the phase 10 plan MUST switch to
`Map.ofEntries()`**. This is not optional.

```java
// BEFORE (9 entries, Map.of):
SECURED_MAPPINGS = Map.of(
    SECURED, ...,
    SECURED_API, ...,
    ACTUATOR, ...,
    REFRESH, ...,
    ADMIN_CLIENTS, ...,
    ADMIN_TOPUPS, ...,
    ADMIN_API_KEYS, ...,
    ADMIN_ANALYTICS, ...,
    ADMIN_SPEND, ...
);

// AFTER (10 entries, Map.ofEntries):
SECURED_MAPPINGS = Map.ofEntries(
    Map.entry(SECURED, Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(SECURED_API, Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(ACTUATOR, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(REFRESH, Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(ADMIN_CLIENTS, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_TOPUPS, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_API_KEYS, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_ANALYTICS, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_SPEND, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_HEALTH, new String[]{AuthoritiesConstants.ADMIN})
);
```

`Map.ofEntries()` returns an unmodifiable map, same semantics as `Map.of()`.

### Anti-Patterns to Avoid

- **Calling `NexahClient.checkAvailability()`**: that makes a live HTTP call to Nexah. The
  health endpoint should read in-memory state from `CircuitBreakerRegistry`, not probe the
  provider.
- **Putting circuit breaker logic in api layer**: inject `CircuitBreakerRegistry` into
  `HealthService`, not into the controller.
- **Using JpaRepository for aggregation**: use `Repository<Object, Long>` per established
  project pattern for pure aggregation repos.
- **Declaring a `@Column status` field**: `WebhookDelivery` uses `attempt_status` column for
  lifecycle state. The inherited `status` column from `AbstractAuditingEntity` is for entity
  lifecycle (always ACTIVE). The SQL must reference `attempt_status`, not `status`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Reading circuit breaker state | Custom state-tracking service | `CircuitBreakerRegistry.circuitBreaker("nexah").getState()` | Registry is the authoritative in-memory source; already in use in SmsService |
| Circuit breaker metrics | Custom counters | `cb.getMetrics().getFailureRate()`, `getNumberOfFailedCalls()` etc. | Built into Resilience4j Metrics interface |
| Webhook delivery stats | Hand-counting delivery rows | Native aggregate SQL COUNT(CASE WHEN) on `webhook_delivery` | Single query, same pattern as phases 8/9 |

---

## Common Pitfalls

### Pitfall 1: FAIL_FINALIZED is a parent status, not a recipient status

**What goes wrong:** Writing SQL against `send_request_recipient.send_status` that includes
`FAIL_FINALIZED` will return 0 rows, because `FAIL_FINALIZED` is only set on the parent
`send_request.send_status`. Recipients reach `FAILED` (not `FAIL_FINALIZED`) after a bad DR.

**Why it happens:** `DrCallbackService.finalizeParentIfAllTerminal()` sets the parent to
`FAIL_FINALIZED`, but the individual recipients stay at `FAILED`.

**How to avoid:** For HLTH-03, use `send_request_recipient.send_status IN ('COMPLETED', 'FAILED')`
for DR-received rows, and `send_status = 'FAILED'` for failure count. Do not include
`FAIL_FINALIZED` in recipient-level queries.

**Warning signs:** `failed_count` is always 0 in testing.

### Pitfall 2: Map.of() limit — silent compile-time error if wrong overload is chosen

**What goes wrong:** If the plan incorrectly uses `Map.of(k1,v1,...,k10,v10)` Java provides
`Map.of(K k1, V v1, ... K k10, V k10)` which accepts exactly 10 pairs. So technically it
would compile. But the prior decision explicitly requires `Map.ofEntries()` for the 10th entry
to future-proof for phase 11+. Using `Map.of()` at 10 entries would break when phase 11 adds
an 11th entry.

**How to avoid:** Phase 10 plan must rewrite `SECURED_MAPPINGS` as `Map.ofEntries(...)`.

### Pitfall 3: Circuit breaker name must match exactly

**What goes wrong:** Using a different name than `"nexah"` in `circuitBreakerRegistry.circuitBreaker(name)`
will create a new instance with default config instead of returning the configured one.

**How to avoid:** Use `"nexah"` — verified from `application.yaml`
(`resilience4j.circuitbreaker.instances.nexah`) and `@CircuitBreaker(name = "nexah")` in `NexahClient`.

### Pitfall 4: `getMetrics()` returns current sliding window state, not lifetime totals

**What goes wrong:** Metrics returned by `cb.getMetrics()` reflect the current sliding window
(configured as `slidingWindowSize: 10`). They reset as the window slides. Don't label these
as "all-time" counts in the response — they are "current window" counts.

**How to avoid:** The response JSON should use `failure_rate_pct` and `buffered_calls` etc.
that convey "current window" semantics, or document them as sliding-window metrics.

### Pitfall 5: Webhook failure_count vs exhausted_count semantics

**What goes wrong:** Treating `FAILED` and `EXHAUSTED` as equivalent in the response.

**Why it matters:** `FAILED` = delivery attempt failed but retries remain. `EXHAUSTED` = all
retries used, no more attempts will be made. HLTH-02 explicitly asks for both counts
separately. The aggregate query must COUNT them separately.

---

## Code Examples

### HLTH-01: Reading Circuit Breaker State

```java
// Source: CircuitBreakerRegistry and CircuitBreaker API — verified from Resilience4j 2.2.0 jar
// and existing usage in SmsService.java lines 26-27, 63, 134-135

CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
CircuitBreaker.State state = cb.getState();  // CLOSED | OPEN | HALF_OPEN | DISABLED | FORCED_OPEN
CircuitBreaker.Metrics metrics = cb.getMetrics();
float failureRate = metrics.getFailureRate();          // -1.0 if window not full yet
int failedCalls   = metrics.getNumberOfFailedCalls();
int successCalls  = metrics.getNumberOfSuccessfulCalls();
long notPermitted = metrics.getNumberOfNotPermittedCalls();
```

Response record:
```java
// Source: pattern from DeliveryStatsResponse.java (record + @JsonProperty)
public record CircuitBreakerHealthResponse(
    @JsonProperty("state")                String  state,          // "CLOSED" | "OPEN" | "HALF_OPEN"
    @JsonProperty("failure_rate_pct")     float   failureRatePct,
    @JsonProperty("failed_calls")         int     failedCalls,
    @JsonProperty("successful_calls")     int     successfulCalls,
    @JsonProperty("not_permitted_calls")  long    notPermittedCalls
) {}
```

### HLTH-02: Webhook Delivery Stats Query

```sql
-- Source: webhook_delivery DDL in V7__webhook.sql (attempt_status column)
SELECT
    COUNT(*)                                                           AS total_attempts,
    COUNT(CASE WHEN d.attempt_status = 'FAILED'     THEN 1 END)       AS failure_count,
    COUNT(CASE WHEN d.attempt_status = 'EXHAUSTED'  THEN 1 END)       AS exhausted_count
FROM main.webhook_delivery d
```

Response record:
```java
public record WebhookHealthResponse(
    @JsonProperty("total_attempts")  long totalAttempts,
    @JsonProperty("failure_count")   long failureCount,
    @JsonProperty("exhausted_count") long exhaustedCount
) {}
```

### HLTH-03: Provider Send Stats Query

```sql
-- Source: send_request_recipient DDL in V5__send_request.sql (send_status column)
-- FAIL_FINALIZED is NOT a valid recipient send_status — recipients reach FAILED (not FAIL_FINALIZED)
SELECT
    COUNT(CASE WHEN r.send_status NOT IN ('ACCEPTED', 'CANCELLED')
               THEN 1 END)                               AS total_submitted,
    COUNT(CASE WHEN r.send_status IN ('COMPLETED', 'FAILED')
               THEN 1 END)                               AS dr_received,
    COUNT(CASE WHEN r.send_status = 'FAILED'
               THEN 1 END)                               AS failed_count
FROM main.send_request_recipient r
```

Failure rate computed in service:
```java
double failureRate = drReceived == 0 ? 0.0 : (double) failedCount / drReceived * 100.0;
```

Response record:
```java
public record ProviderStatsResponse(
    @JsonProperty("total_submitted")  long   totalSubmitted,
    @JsonProperty("dr_received")      long   drReceived,
    @JsonProperty("failed_count")     long   failedCount,
    @JsonProperty("failure_rate_pct") double failureRatePct
) {}
```

### AppEndpoints — Map.ofEntries() Replacement

```java
// Source: AppEndpoints.java static block (current state) — must be rewritten for phase 10
// New constant to add alongside the others:
public static final String ADMIN_HEALTH = "/api/admin/health/**";

// Static block — replace Map.of() with Map.ofEntries():
SECURED_MAPPINGS = Map.ofEntries(
    Map.entry(SECURED,        Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(SECURED_API,    Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(ACTUATOR,       new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(REFRESH,        Arrays.copyOf(SECURED_AUTHORITIES, SECURED_AUTHORITIES.length)),
    Map.entry(ADMIN_CLIENTS,  new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_TOPUPS,   new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_API_KEYS, new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_ANALYTICS,new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_SPEND,    new String[]{AuthoritiesConstants.ADMIN}),
    Map.entry(ADMIN_HEALTH,   new String[]{AuthoritiesConstants.ADMIN})
);
```

### HealthRepository (aggregation repo pattern)

```java
// Source: pattern from DeliveryAnalyticsRepository.java and SpendRepository.java
@org.springframework.stereotype.Repository
public interface HealthRepository extends Repository<Object, Long> {

    @Query(value = """
        SELECT
            COUNT(*) AS total_attempts,
            COUNT(CASE WHEN d.attempt_status = 'FAILED'    THEN 1 END) AS failure_count,
            COUNT(CASE WHEN d.attempt_status = 'EXHAUSTED' THEN 1 END) AS exhausted_count
        FROM main.webhook_delivery d
        """, nativeQuery = true)
    WebhookStatsRow findWebhookStats();

    @Query(value = """
        SELECT
            COUNT(CASE WHEN r.send_status NOT IN ('ACCEPTED', 'CANCELLED') THEN 1 END) AS total_submitted,
            COUNT(CASE WHEN r.send_status IN ('COMPLETED', 'FAILED')        THEN 1 END) AS dr_received,
            COUNT(CASE WHEN r.send_status = 'FAILED'                        THEN 1 END) AS failed_count
        FROM main.send_request_recipient r
        """, nativeQuery = true)
    ProviderStatsRow findProviderStats();
}
```

### HealthService — single service injecting both sources

```java
// Source: pattern from DeliveryAnalyticsService.java and SpendService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class HealthService {

    private final HealthRepository healthRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerHealthResponse getCircuitBreakerHealth() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
        CircuitBreaker.Metrics m = cb.getMetrics();
        return new CircuitBreakerHealthResponse(
            cb.getState().name(),
            m.getFailureRate(),
            m.getNumberOfFailedCalls(),
            m.getNumberOfSuccessfulCalls(),
            m.getNumberOfNotPermittedCalls()
        );
    }

    public WebhookHealthResponse getWebhookHealth() { ... }
    public ProviderStatsResponse getProviderStats() { ... }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Map.of()` for SECURED_MAPPINGS (9 entries) | `Map.ofEntries()` (10+ entries) | Phase 10 | AppEndpoints must be rewritten to use Map.ofEntries |

**No deprecated patterns** apply to this phase's domain.

---

## Open Questions

1. **Should `failureRate` return -1.0 before the sliding window is full?**
   - What we know: Resilience4j `getFailureRate()` returns `-1.0f` when the sliding window
     has not yet collected enough calls to compute a rate (before `slidingWindowSize: 10`
     calls have completed).
   - What's unclear: Whether the response should pass `-1.0` through, or convert it to `0.0`.
   - Recommendation: Pass through `-1.0` with a note in the field description, or convert
     to `null` for the JSON field. Either is acceptable — plan should decide and document.

2. **Time-windowed vs all-time for HLTH-02 and HLTH-03?**
   - What we know: Phase 8 and 9 endpoints accepted optional `from`/`to` filters. HLTH-02
     and HLTH-03 requirements don't mention time filters — they say "query stats" without
     a filter requirement.
   - What's unclear: Whether the admin wants "all-time totals" or "today's stats".
   - Recommendation: Start with all-time totals (no filters). Simpler implementation, and
     the requirement text doesn't specify filters. Can add filters in a later phase.

---

## Sources

### Primary (HIGH confidence)
- Codebase: `SmsService.java` lines 26-27, 63, 134-135 — `CircuitBreakerRegistry` injection and
  `.circuitBreaker("nexah").getState()` usage verified directly
- Codebase: `NexahClient.java` — `@CircuitBreaker(name = "nexah")` annotation verified
- Codebase: `application.yaml` — `resilience4j.circuitbreaker.instances.nexah` config verified
- Codebase: `WebhookDelivery.java` + `V7__webhook.sql` — `attempt_status` column and
  `WebhookDeliveryStatus` enum values (PENDING/DELIVERED/FAILED/EXHAUSTED) verified
- Codebase: `SendRequestRecipient.java` + `V5__send_request.sql` — `send_status` column and
  `SendRequestStatus` enum values verified
- Codebase: `DrCallbackService.java` — `FAIL_FINALIZED` is only set on parent `send_request`,
  not on `send_request_recipient` — verified from code
- Codebase: `AppEndpoints.java` — current 9-entry `Map.of()` confirmed
- Codebase: `DeliveryAnalyticsRepository.java`, `SpendRepository.java` — `Repository<Object, Long>`
  aggregation pattern verified
- Jar inspection: `resilience4j-circuitbreaker-2.2.0.jar` — `CircuitBreaker.State` enum values
  (CLOSED/OPEN/HALF_OPEN/DISABLED/METRICS_ONLY/FORCED_OPEN) and `CircuitBreaker.Metrics`
  interface methods verified via `javap`

### Secondary (MEDIUM confidence)
- None needed — all critical claims verified from primary sources

---

## Metadata

**Confidence breakdown:**
- HLTH-01 circuit breaker state: HIGH — `CircuitBreakerRegistry` injection verified in codebase;
  `getState()` API verified from jar
- HLTH-02 webhook stats: HIGH — table structure, column names, and enum values verified from DDL
  and entity class
- HLTH-03 provider stats: HIGH — table structure and status values verified; note on
  FAIL_FINALIZED being parent-only is confirmed from DrCallbackService
- AppEndpoints Map.ofEntries migration: HIGH — 9 entries confirmed by direct count; Java limit
  confirmed; prior decision documented
- Architecture pattern: HIGH — follows established phases 8/9 structure exactly

**Research date:** 2026-03-11
**Valid until:** 2026-04-11 (stable codebase — no external dependencies changing)
