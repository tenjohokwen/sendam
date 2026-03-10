# Phase 4: Provider Integration & Message Status — Research

**Researched:** 2026-03-10
**Domain:** Nexah HTTP client, SMS state machine, billing settlement, DR callback security, message purge
**Confidence:** HIGH (all findings grounded in codebase inspection and official Nexah spec)

---

## Summary

Phase 4 closes the delivery loop that Phase 3 opened. Phase 3 accepts SMS requests, reserves credits, persists rows, and stubs SUBMITTED via `SmsSchedulerService`. Phase 4 replaces that stub with a real Nexah HTTP call, receives delivery reports (DR callbacks) from Nexah, advances the per-recipient state machine to terminal states, settles billing using provider-confirmed segment counts, and adds a 30-day purge job.

The Nexah API is simple: one POST endpoint to send SMS, one incoming webhook for delivery reports. No OAuth or complex authentication — Nexah credentials (user/password) are embedded in every send request body. The DR callback is Nexah-initiated, so it must be reachable without the client API-key authentication chain.

The codebase already has `resilience4j` on the classpath (`spring-cloud-starter-circuitbreaker-resilience4j`) and `spring-retry`. It already uses `RestTemplate` via `AbstractClient`/`Client` for outbound HTTP calls. All billing primitives (`CreditReservationService.debit()`, `LedgerEntryType.SMS_REFUND`) are already implemented and correct. The schema already has `gateway_message_id`, `provider_message_id`, and `segments_consumed` on `send_request_recipient`. No schema migration is required for recipient columns; one migration is required to add `finalized_at` to `send_request` for the 30-day purge window.

**Primary recommendation:** Use `RestTemplate` (the existing infrastructure pattern) for the Nexah HTTP client. Add a dedicated security filter chain at `@Order(0)` that permits `/v1/provider/**` without API-key authentication. Use Resilience4j `CircuitBreaker` for provider availability — it is already on the classpath and integrates with Spring Boot actuator health.

---

## Standard Stack

### Core (all already in pom.xml)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-cloud-starter-circuitbreaker-resilience4j` | 2025.0.1 (BOM) | Circuit breaker for Nexah availability | Already on classpath; preferred Spring Cloud CB implementation |
| `spring-retry` | (Spring Boot BOM) | @Retryable on Nexah send attempts | Already on classpath |
| `spring-boot-starter-web` (RestTemplate) | 3.5.11 | Outbound HTTP to Nexah | Matches AbstractClient pattern in `common.client` |
| `spring-boot-starter-scheduling` | 3.5.11 | 30-day purge job | Already enabled via `ClientConfig.@EnableScheduling` |

### No new dependencies required
All Phase 4 needs is already in the pom. No additions needed.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| RestTemplate | WebClient | WebClient is reactive; project is blocking/servlet-based with no reactive stack — switch would be disruptive |
| RestTemplate | RestClient (Spring 6.1+) | RestClient is cleaner but project already has AbstractClient pattern with RestTemplate; consistency wins |
| Resilience4j CircuitBreaker | Simple AtomicBoolean flag | CircuitBreaker gives half-open recovery, metrics, actuator health exposure — no reason to reinvent |

---

## Architecture Patterns

### Recommended Package Structure (additions to `client/`)
```
client/
├── infrastructure/
│   ├── filter/              # existing: ApiKeyAuthenticationFilter
│   └── nexah/               # NEW: NexahClient (wraps RestTemplate calls to Nexah)
├── service/
│   ├── SmsService.java      # existing
│   ├── SmsSchedulerService.java  # MODIFIED: replace stub with real dispatch
│   ├── NexahDispatchService.java # NEW: orchestrates send + state transitions
│   ├── DrCallbackService.java    # NEW: processes incoming DR payloads
│   └── SmsPurgeService.java      # NEW: @Scheduled 30-day purge
├── api/
│   ├── SmsResource.java     # existing
│   └── DrCallbackResource.java  # NEW: POST /v1/provider/dr
├── contract/
│   ├── nexah/               # NEW: NexahSendRequest, NexahSendResponse, NexahDrPayload
│   └── exception/
│       └── ProviderUnavailableException.java  # NEW
└── config/
    ├── ClientConfig.java    # existing: @EnableScheduling
    ├── ClientSecurityConfiguration.java  # existing: @Order(1) API key chain
    └── NexahSecurityConfiguration.java  # NEW: @Order(0) permit /v1/provider/**
```

### Pattern 1: Nexah HTTP Client using AbstractClient convention

The project's `common.client.AbstractClient` is the established pattern for all outbound HTTP clients. `NexahClient` extends it (or mirrors it without extending, since AbstractClient is in `common`).

```java
// Source: /Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/common/client/AbstractClient.java
// Pattern: RestTemplate + RestRequestInterceptor + explicit host+path config
@Component
public class NexahClient {
    private final RestTemplate restTemplate;
    private final NexahProperties properties; // @ConfigurationProperties in contract/

    public NexahSendResponse sendSms(NexahSendRequest request) {
        // POST https://smsvas.com/bulk/public/index.php/api/v1/sendsms
        // Body: { user, password, senderid, sms, mobiles (comma-separated) }
    }

    public NexahCreditResponse checkCredit() {
        // POST https://smsvas.com/bulk/public/index.php/api/v1/smscredit
        // Used for availability check — if this returns 200 + responsecode=1, provider is up
    }
}
```

### Pattern 2: SmsSchedulerService replacement — dispatch loop

Phase 3 marks rows SUBMITTED without calling Nexah. Phase 4 replaces this:

```java
// BEFORE (Phase 3 stub):
request.setSendStatus(SendRequestStatus.SUBMITTED);
sendRequestRepository.save(request);

// AFTER (Phase 4):
// dispatchScheduledMessages() delegates to NexahDispatchService.dispatch(request)
// NexahDispatchService:
//   1. Check circuit breaker is CLOSED (provider available)
//   2. Collect recipients for this send request
//   3. Build comma-separated mobiles string
//   4. POST to Nexah sendsms
//   5. For each recipient in response:
//      - set gateway_message_id = sms[i].messageid
//      - set provider_message_id = sms[i].smsclientid
//      - set sendStatus = SUBMITTED
//   6. Set send_request.sendStatus = SUBMITTED
//   7. Save both entities in one @Transactional
```

Important: The scheduler also needs to catch `ProviderUnavailableException` and NOT mark the request SUBMITTED. Immediate sends (`scheduleTime == null`) also hit the circuit breaker at send time and reject with 503 if open.

### Pattern 3: DR Callback endpoint — open to Nexah, no client auth

Nexah POSTs to a partner-configured URL. This endpoint must NOT require the API key Bearer token. A separate `SecurityFilterChain` at `@Order(0)` permits `/v1/provider/**` without authentication:

```java
// Source: /Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java
// Pattern: @Order(1) chain for /v1/** — add @Order(0) BEFORE it for /v1/provider/**
@Configuration
@Order(0)  // Must be before @Order(1) client chain
public class NexahSecurityConfiguration {
    @Bean
    @Order(0)
    public SecurityFilterChain nexahCallbackFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/provider/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

Nexah does not send any authentication header on DR callbacks — the endpoint is secured by obscurity (the URL is secret) and optionally by IP allowlisting. Do NOT add Bearer authentication to this endpoint.

### Pattern 4: DR callback processing

The DR callback body contains a `dlrlist` array. Each entry has `messageid` (which equals `gateway_message_id` stored in `send_request_recipient`).

```java
// Nexah DR payload structure (from nexahApi.md):
// { "dlrlist": [{ "reponsecode": "1", "messageid": "...", "mobileno": "...",
//                 "total_sms_unit": "2", "status": "DELIVRD"|"UNDELIV", ... }] }

// DrCallbackService.processDr(NexahDrPayload payload):
// For each dlr entry:
//   1. Find SendRequestRecipient by gatewayMessageId = dlr.messageid
//   2. Set segmentsConsumed = dlr.total_sms_unit (convert String to Integer)
//   3. Set sendStatus = COMPLETED (if DELIVRD) or FAILED (if UNDELIV)
//   4. If all recipients for send_request are terminal → finalize parent:
//      a. Set send_request.sendStatus = FINALIZED or FAIL_FINALIZED
//      b. Set send_request.finalizedAt = now()
//      c. Call CreditReservationService.debit(clientId, reservationId, totalActualSegments)
//         — this writes SMS_DEBIT + SMS_REFUND (if over-reservation) and adjusts balance
// 5. Return dlrlist with status=0 for entries that failed to process (signal nexah to retry)
// 6. Return dlrlist with status=1 for entries successfully processed
```

### Pattern 5: Billing settlement on finalization

The billing logic is already fully implemented in `CreditReservationService.debit()`. Phase 4 only needs to call it correctly:

- `reservationId` is stored on `SendRequest.reservationId` — read it from there
- `actualAmount` = sum of `segmentsConsumed` across all recipients for this send request
- `debit()` handles the over-reservation refund internally (writes SMS_DEBIT + SMS_REFUND, adjusts balance)
- Per prior decisions: `debit()` does NOT subtract actualAmount from balance again — it only adjusts upward for over-reservation

### Pattern 6: Provider availability check

Use Resilience4j `CircuitBreaker`. The `smscredit` endpoint is the availability probe — it accepts credentials and returns `responsecode: 1` on success.

The circuit breaker wraps the Nexah send operation. On open circuit, throw `ProviderUnavailableException` → ApiAdvice maps to 503 with `PROVIDER_UNAVAILABLE`.

For immediate sends (`/v1/sms/send` with no scheduleTime), check circuit breaker state before completing the transaction. If open, throw `ProviderUnavailableException` — no credits are touched because this check happens after reservation in the send flow... wait — this is a key design decision.

**Critical sequence for immediate sends when provider unavailable:**
The v8 contract says "Balance unchanged" when provider unavailable. This means for immediate sends, the check must happen BEFORE credit reservation, or the reservation must be released immediately.

There are two clean options:
1. Check circuit breaker BEFORE reserve() in SmsService.sendSms — if open, throw immediately with no credits touched
2. Allow reservation, attempt dispatch, release() on ProviderUnavailableException

Option 1 is cleaner and matches the contract wording. The circuit breaker probe is fast (in-memory state check — no network call). **Use option 1: check circuit breaker state before reserve() in SmsService.sendSms.**

For scheduled sends, the schedule time means Nexah may become available before dispatch. The scheduler checks the circuit breaker at dispatch time and skips sending if open (leaves the request in ACCEPTED, retries next poll cycle).

### Pattern 7: 30-day purge job

Purge `send_request_recipient` rows where the parent `send_request.finalized_at` is more than 30 days ago, then purge the parent `send_request` row. Foreign key constraint requires child delete first.

```java
// SmsPurgeService — @Scheduled(cron = "0 0 2 * * *")  (daily at 2am)
// @Transactional
// 1. Find send_request IDs where finalized_at < now - 30 days AND sendStatus IN (FINALIZED, FAIL_FINALIZED)
// 2. Batch-delete from send_request_recipient where send_request_id_fk IN (...)
// 3. Batch-delete from send_request where id IN (...)
// getStatus() returns 404 (ResourceNotFoundException → existing 404 handler) when send_request not found
```

`finalized_at` column must be added to `send_request` via a new Flyway migration (V6).

### Anti-Patterns to Avoid

- **Do not use WebClient:** Project is servlet/blocking, no reactive infrastructure. WebClient with RestTemplate mixing is unnecessarily complex.
- **Do not check circuit breaker state inside DrCallbackService:** DR callbacks come from Nexah, not to it. The circuit breaker guards outbound calls only.
- **Do not call `CreditService.applyLedgerEntry()` from Phase 4:** Prior decision says `CreditReservationService.debit()` is the correct path for settling reservations — it already handles lock acquisition, SMS_DEBIT, and over-reservation SMS_REFUND internally.
- **Do not finalize per-recipient:** Finalization (debit + ledger + parent status) happens once per SendRequest when ALL recipients reach a terminal state, not per recipient.
- **Do not DELETE recipients before parent in purge:** Foreign key `send_request_recipient.send_request_id_fk` references `send_request`. Always delete children first.
- **Do not skip the DR retry loop:** Nexah expects you to return failed DLRs in the response body with `status=0`. If you return an empty array or all `status=1`, Nexah will not retry. Return only the entries that failed processing.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Provider availability detection | Boolean flag + polling thread | Resilience4j CircuitBreaker (already on classpath) | Handles half-open recovery, metrics, thread-safety automatically |
| HTTP retry on Nexah timeouts | Manual retry loop | `@Retryable` (spring-retry, already on classpath) | Declarative, handles backoff, plugs into existing AOP |
| Outbound HTTP to Nexah | Any new HTTP library | RestTemplate via existing AbstractClient pattern | Consistency with `common.client` infrastructure |

**Key insight:** The hardest part of this phase is not HTTP — it's the state machine and billing settlement. Both are already mostly wired (`SendRequestStatus` enum complete, `CreditReservationService.debit()` complete). Phase 4 is primarily about connecting the wiring, not building new primitives.

---

## Common Pitfalls

### Pitfall 1: Provider circuit breaker checked too late for immediate sends
**What goes wrong:** SmsService.sendSms reserves credits, then dispatches to Nexah. Provider is unavailable. Now credits are reserved but dispatch failed. Code releases the reservation but the atomicity guarantee in the v8 contract ("Balance unchanged") requires that no reservation ever touched the balance.
**Why it happens:** Temptation to treat reserve+dispatch as one unit.
**How to avoid:** Check circuit breaker state (in-memory read, no network call) BEFORE `creditReservationService.reserve()` in `SmsService.sendSms`. If circuit is OPEN, throw `ProviderUnavailableException` immediately — no credits touched.
**Warning signs:** Test where provider is unavailable shows `SMS_RESERVATION` ledger entry that gets immediately followed by `SMS_REFUND`.

### Pitfall 2: Nexah DR `total_sms_unit` is a String, not an Integer
**What goes wrong:** The DR payload declares `total_sms_unit` as an Integer but the example shows `"2"` (quoted string). Jackson deserialization will fail if the DTO field is `int` or `Integer`.
**Why it happens:** Nexah API inconsistency in the spec.
**How to avoid:** Declare `NexahDrEntry.totalSmsUnit` as `String` (or use `@JsonDeserialize(using = StringToIntDeserializer.class)`). Parse to int explicitly.
**Warning signs:** `com.fasterxml.jackson.databind.exc.InvalidFormatException` in DR callback logs.

### Pitfall 3: Same pattern for `reponsecode` in DR — note the typo
**What goes wrong:** The Nexah DR spec spells the field `reponsecode` (not `responsecode`). Jackson default naming won't match.
**Why it happens:** Nexah typo in their API spec (confirmed by example — both request and response use `reponsecode`).
**How to avoid:** Use `@JsonProperty("reponsecode")` on the DTO field. Verify against the example, not the table header.

### Pitfall 4: Parent send_request finalization is triggered by recipient state, not by time
**What goes wrong:** Finalizing the parent immediately when the last DR arrives is correct. But if Nexah never sends a DR for some recipients (e.g. network issue), the send request stays SUBMITTED forever and credits remain reserved.
**Why it happens:** Nexah does not guarantee DR delivery for all recipients.
**How to avoid:** Add a fallback: a scheduled job (can be added to SmsPurgeService or a separate job) that marks stale SUBMITTED requests (SUBMITTED for > N hours with unreported recipients) as FAIL_FINALIZED and calls `debit()` with segments_consumed for the recipients that did report, or `debit()` with the full reserved amount as a conservative fallback. This is a Phase 4 concern even if the contract does not spell it out — otherwise stale reservations accumulate.
**Warning signs:** send_request rows stuck in SUBMITTED with no DR callback after 24+ hours.

### Pitfall 5: Nexah sends DLR for `messageid` not in DB (duplicate delivery)
**What goes wrong:** Nexah may re-send a DR for a messageid that has already been processed. The second call finds a FINALIZED recipient, tries to finalize again, or runs debit() twice.
**Why it happens:** The Nexah protocol docs say you can signal failure to cause a retry — but retries can also happen spontaneously.
**How to avoid:** In DrCallbackService, before processing a DR entry: if recipient.sendStatus is already FINALIZED or FAIL_FINALIZED, skip processing (it's already done) and return `status=1` in the response (acknowledge as success so Nexah stops retrying).
**Warning signs:** Duplicate `SMS_DEBIT` ledger entries for the same send request.

### Pitfall 6: 30-day purge and paginated queries in getStatus()
**What goes wrong:** After a send_request is purged, `getStatus()` throws `ResourceNotFoundException`. The existing handler returns 404 — which is exactly the contract requirement ("queries return 404 after the window"). But if `getStatus()` is called while a purge transaction is in-flight, a partial result is possible.
**Why it happens:** Purge and status query run in separate transactions.
**How to avoid:** Use `findByClientIdAndSendRequestId` with `PESSIMISTIC_READ` lock during purge, or accept the slim race window (purge runs at 2am, low traffic). The standard approach is to accept the slim race.

---

## Code Examples

### Nexah Send Request DTO

```java
// Source: requirements/nexahApi.md — section 2.1.1
public record NexahSendRequest(
    @JsonProperty("user")     String user,
    @JsonProperty("password") String password,
    @JsonProperty("senderid") String senderid,
    @JsonProperty("sms")      String sms,
    @JsonProperty("mobiles")  String mobiles  // comma-separated: "657114646, 658420169"
) {}
```

### Nexah Send Response DTO

```java
// Source: requirements/nexahApi.md — section 2.1.2
public record NexahSendResponse(
    @JsonProperty("responsecode")        int responseCode,    // 1=success, 0=error
    @JsonProperty("responsedescription") String responseDescription,
    @JsonProperty("responsemessage")     String responseMessage,
    @JsonProperty("sms")                 List<NexahSmsEntry> sms
) {}

public record NexahSmsEntry(
    @JsonProperty("status")         String status,           // "success" or "error"
    @JsonProperty("smsclientid")    String smsClientId,      // maps to provider_message_id
    @JsonProperty("messageid")      String messageId,        // maps to gateway_message_id
    @JsonProperty("mobileno")       String mobileNo,
    @JsonProperty("errorcode")      Integer errorCode,
    @JsonProperty("errordescription") String errorDescription,
    @JsonProperty("total_sms_unit") Integer totalSmsUnit,    // confirmed Integer in send response
    @JsonProperty("balance")        Integer balance
) {}
```

### Nexah DR Callback DTO

```java
// Source: requirements/nexahApi.md — section 2.3.1
// NOTE: "reponsecode" is a typo in the Nexah spec — match exactly
public record NexahDrPayload(
    @JsonProperty("dlrlist") List<NexahDrEntry> dlrList
) {}

public record NexahDrEntry(
    @JsonProperty("reponsecode")        String responseCode,    // "1"=success, "0"=fail (String!)
    @JsonProperty("reponsedescription") String responseDescription,
    @JsonProperty("mobileno")           String mobileNo,
    @JsonProperty("messageid")          String messageId,       // matches gateway_message_id
    @JsonProperty("total_sms_unit")     String totalSmsUnit,    // String in DR! "2" not 2
    @JsonProperty("submittime")         String submitTime,
    @JsonProperty("senttime")           String sentTime,
    @JsonProperty("deliverytime")       String deliveryTime,
    @JsonProperty("status")             String status,          // "DELIVRD" or "UNDELIV"
    @JsonProperty("traffic")            String traffic
) {}
```

### DR Callback Response DTO

```java
// Source: requirements/nexahApi.md — section 2.3.2
// Return failed entries with status=0 to trigger Nexah retry
// Return successful entries with status=1 to acknowledge
public record NexahDrResponse(
    @JsonProperty("dlrlist") List<NexahDrAck> dlrList
) {}

public record NexahDrAck(
    @JsonProperty("reponsecode")        int responseCode,
    @JsonProperty("reponsedescription") String responseDescription,
    @JsonProperty("messageid")          String messageId,
    @JsonProperty("mobileno")           String mobileNo,
    @JsonProperty("status")             int status,   // 1=success processed, 0=failed (retry)
    @JsonProperty("submittime")         String submitTime,
    @JsonProperty("senttime")           String sentTime,
    @JsonProperty("deliverytime")       String deliveryTime
) {}
```

### State machine transition table

```
Recipient states:
  ACCEPTED → SUBMITTED  (NexahDispatchService after successful Nexah API call)
  SUBMITTED → COMPLETED  (DrCallbackService when DR status="DELIVRD")
  SUBMITTED → FAILED     (DrCallbackService when DR status="UNDELIV")
  COMPLETED → FINALIZED  (DrCallbackService when all recipients terminal)
  FAILED → FAIL_FINALIZED (DrCallbackService when all recipients terminal)

Parent SendRequest states (derived from recipients):
  ACCEPTED → SUBMITTED   (when Nexah call succeeds, even partially)
  SUBMITTED → FINALIZED  (when all recipients reach COMPLETED → FINALIZED)
  SUBMITTED → FAIL_FINALIZED (when all recipients terminal and at least one FAILED)

Note: Mixed result (some COMPLETED, some FAILED) → parent = FAIL_FINALIZED
      (conservative; billing still settles on actual sum of segments_consumed)
```

### Flyway migration V6 — add finalized_at column

```sql
-- V6__send_request_finalized_at.sql
ALTER TABLE main.send_request
    ADD COLUMN finalized_at TIMESTAMP;

CREATE INDEX idx_send_request_finalized_at ON main.send_request(finalized_at)
    WHERE finalized_at IS NOT NULL AND send_status IN ('FINALIZED', 'FAIL_FINALIZED');
```

### NexahSecurityConfiguration (DR endpoint security)

```java
// Source: pattern from ClientSecurityConfiguration.java
@Configuration
@Order(0)  // Before @Order(1) API key chain
public class NexahSecurityConfiguration {
    @Bean
    @Order(0)
    public SecurityFilterChain nexahCallbackFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/provider/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

### Circuit breaker configuration (application.yaml addition)

```yaml
resilience4j.circuitbreaker:
  instances:
    nexah:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 30s
      permittedNumberOfCallsInHalfOpenState: 3
      registerHealthIndicator: true

nexah:
  user: ${NEXAH_USER}
  password: ${NEXAH_PASSWORD}
  senderid: ${NEXAH_SENDERID}
  baseUrl: https://smsvas.com/bulk/public/index.php/api/v1
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring RetryTemplate (programmatic) | `@Retryable` AOP annotation | Spring Retry 1.x | Declarative retry, less boilerplate |
| Custom circuit breaker | Resilience4j via Spring Cloud CB | Spring Cloud 2020+ | Actuator health integration free |
| RestTemplate (blocking) | WebClient (reactive) | Spring 5+ | Not applicable here — project is servlet |

**No state-of-the-art changes required:** The existing blocking RestTemplate + Resilience4j + Spring Retry stack is appropriate for this project's architecture.

---

## Entity/Column Changes Required

### `send_request` entity
- Add `finalizedAt` (`Instant`) field — needed for 30-day purge window
- Flyway migration V6 adds `finalized_at TIMESTAMP` column

### `send_request_recipient` entity
- No changes needed — `gateway_message_id`, `provider_message_id`, `segments_consumed` already exist (verified in V5 migration and entity class)

### `SendRequest` (Java entity) additions
```java
@Column(name = "finalized_at")
private Instant finalizedAt;  // set when sendStatus transitions to FINALIZED or FAIL_FINALIZED
```

---

## Open Questions

1. **Nexah DR retry limit**
   - What we know: Nexah docs say "Consider limiting the number of times you signal this to nexah so as not to remain in an unending loop" — they explicitly warn about returning `status=0` causing infinite retry
   - What's unclear: Maximum retry count before abandoning. Not specified in docs.
   - Recommendation: Track a `dr_retry_count` column on `send_request_recipient` OR simply: after a recipient has been reported by DR once, always return `status=1` regardless of processing result (idempotency guard covers this). The simpler rule: if recipient is already in a terminal state, always ack with `status=1`.

2. **Stale SUBMITTED rows (no DR ever received)**
   - What we know: Nexah does not guarantee DR delivery
   - What's unclear: Whether the planner wants a "stale DR" recovery job in Phase 4 or deferred
   - Recommendation: Include a lightweight stale-recovery step in Phase 4 scope. A scheduled job that identifies SUBMITTED recipients with no DR after 24 hours and force-finalizes with FAIL_FINALIZED. This prevents credit reservation leaks.

3. **Immediate sends — does the scheduler also handle them?**
   - What we know: `SmsSchedulerService.findDueScheduledRequests()` only finds rows where `scheduleTime IS NOT NULL`. Immediate sends (`scheduleTime = null`) are NOT picked up by the scheduler.
   - What's unclear: The spec says "At schedule time → submitted to provider" for scheduled. For immediate sends, Phase 3 does NOT submit to provider — it just reserves credits. Phase 4 must also dispatch immediate sends.
   - Recommendation: The scheduler query must be extended OR a second dispatch path exists. Options: (a) extend scheduler to also pick up ACCEPTED rows with `scheduleTime IS NULL` for dispatch, or (b) dispatch immediately inline in `SmsService.sendSms` after reserve. Option (b) is simpler and gives instant feedback but couples the send endpoint to Nexah latency. Option (a) is decoupled but adds 0-30s delay to "immediate" sends. The planner must decide — this research flags the gap.

---

## Sources

### Primary (HIGH confidence)
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/requirements/nexahApi.md` — Nexah API spec (send SMS, DR callback, balance check)
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/requirements/client-facing_API_contract_v8.md` — client contract (state machine, retention, error codes)
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/client/service/CreditReservationService.java` — billing settlement implementation
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/client/service/SmsSchedulerService.java` — existing scheduler stub
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/client/repo/SendRequest.java` and `SendRequestRecipient.java` — entity columns
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/resources/db/migration/V5__send_request.sql` — confirmed schema columns
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/pom.xml` — confirmed resilience4j, spring-retry, RestTemplate all on classpath
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java` — security chain ordering pattern
- `/Users/mokwen/dev/gitrepos/bluegithub/sendam/.planning/STATE.md` — prior decisions

---

## Metadata

**Confidence breakdown:**
- Nexah API structure: HIGH — read from official requirements spec
- Standard stack: HIGH — verified in pom.xml
- State machine: HIGH — `SendRequestStatus` enum complete, all states present
- Billing settlement: HIGH — `CreditReservationService.debit()` fully implemented and correct
- DR endpoint security: HIGH — pattern taken directly from `ClientSecurityConfiguration`
- 30-day purge: HIGH — straightforward scheduler pattern, existing `@EnableScheduling` in place
- Immediate send dispatch gap: HIGH — confirmed by reading scheduler query (scheduleTime IS NOT NULL)
- Nexah DR type quirks (String total_sms_unit, reponsecode typo): HIGH — verified in spec examples

**Research date:** 2026-03-10
**Valid until:** 2026-04-10 (Nexah API stable; Spring Boot 3.5.x stable)
