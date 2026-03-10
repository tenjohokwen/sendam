# Phase 3: Send SMS & Credit Reservation — Research

**Researched:** 2026-03-10
**Domain:** SMS request lifecycle, idempotency, segment billing, scheduled messages, Spring @Scheduled, state machine, security chain wiring
**Confidence:** HIGH (codebase verified directly) / MEDIUM (Spring @Scheduled patterns from training, validated against known API)

---

## Summary

Phase 3 implements the SMS send flow from client request to credit reservation and acceptance. The foundation (CreditReservationService, RateLimitingService N-token overload) was built in Phases 1 and 2 specifically to be wired here. This phase is primarily about assembling existing components rather than building new infrastructure.

The core data model requires two new entities: `SendRequest` (the idempotency anchor, one row per `sendRequestId` per client) and `SendRequestRecipient` (one row per phone number per request). The UNIQUE constraint `(client_id, send_request_id)` on `send_request` is the database-level idempotency guarantee. SMS segment calculation (GSM-7/UCS-2 detection and segment count) must not be hand-rolled — a utility method within the SMS service is sufficient since the formula is simple and deterministic.

The scheduled SMS model is straightforward: `scheduleTime` is an optional column on `SendRequest`. A Spring `@Scheduled` poller fires frequently (every 30s is appropriate), queries for `ACCEPTED` requests whose `scheduleTime <= now()`, and submits them to the provider. Cancel (Phase 3 scope, Plan 03-03) transitions state to `CANCELLED` and calls `CreditReservationService.release()`.

A critical security wiring issue was discovered: the existing `ClientSecurityConfiguration` uses `securityMatcher("/v1/api/**")` but credit/topup endpoints are at `/v1/credits/**`. For Phase 3, the `SmsResource` at `/v1/sms/**` must be covered by the API key security chain. The security matcher must be expanded OR the SMS endpoint must be added to the existing `/v1/api/**` matcher. The planner must decide which path to take.

**Primary recommendation:** Add `/v1/sms/**` and `/v1/credits/**` to the API key security chain by widening the securityMatcher to cover all `/v1/**` paths, OR add a second API-key-protected chain specifically for these paths. Widening is simpler. The JWT chain at @Order(2) also covers `/v1/**` — Spring Security evaluates chains in order, so @Order(1) takes precedence for matched paths.

---

## Standard Stack

All libraries are already present in pom.xml. No new dependencies are required for Phase 3.

### Core (already in pom.xml)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| spring-boot-starter-data-jpa | 3.5.11 managed | JPA entities, repositories, @Lock | Already used |
| postgresql | managed | JDBC driver, SELECT FOR UPDATE | Already used |
| flyway-core | managed | DB schema migrations | Already used |
| hypersistence-utils-hibernate-63 | present | @Tsid primary key generation via BaseEntity | Already used in all entities |
| lombok | managed | @SuperBuilder, @NoArgsConstructor, @Slf4j | Already used |
| spring-boot-starter-validation | managed | @Valid, @NotNull, @Size on request DTOs | Already used |
| bucket4j-core | present | RateLimitingService N-token tryConsume | Already wired in Phase 1 |

**Installation:** No new Maven dependencies needed.

---

## Architecture Patterns

### Recommended Package Structure

All new code lives in the `client` module, adding an `sms` sub-package alongside existing sub-packages:

```
src/main/java/com/softropic/sendam/client/
├── api/
│   └── SmsResource.java               NEW — POST /v1/sms/send, DELETE /v1/sms/scheduled/{sendRequestId}, GET /v1/sms/status/{sendRequestId}
├── service/
│   ├── SmsService.java                NEW — orchestrates validation, reservation, persistence
│   └── SmsSchedulerService.java       NEW — @Scheduled poller for scheduled SMS dispatch
├── repo/
│   ├── SendRequest.java               NEW — @Entity for send_request table
│   ├── SendRequestRepository.java     NEW — findByClientIdAndSendRequestId, findScheduledForDispatch
│   ├── SendRequestRecipient.java      NEW — @Entity for send_request_recipient table
│   └── SendRequestRecipientRepository.java  NEW — findBySendRequestId
└── contract/
    ├── SendSmsRequest.java            NEW — record for POST /v1/sms/send body
    ├── SendSmsResponse.java           NEW — record for accepted response
    ├── MessageStatusResponse.java     NEW — record for GET /v1/sms/status/{sendRequestId}
    ├── MessageStatusEntry.java        NEW — record for individual recipient status in response
    ├── SendRequestStatus.java         NEW — enum: ACCEPTED, SUBMITTED, COMPLETED, FAILED, FINALIZED, FAIL_FINALIZED, CANCELLED
    └── exception/SmsError.java        NEW — ErrorCode enum: INVALID_PHONE_NUMBER, DUPLICATE_SEND_REQUEST_ID, INVALID_SCHEDULE_TIME, INVALID_SENDER_ID, DUPLICATE_SEND_REQUEST_ID_EXCEPTION
```

### Pattern 1: All-or-Nothing Validation Before Reservation

Per SMS-02, any invalid recipient or insufficient balance rejects the entire request. Validation happens before credit reservation, in this order:

```
1. Validate sendRequestId uniqueness (if exists → 409, return existing response)
2. Validate sender ID (regex: ^[A-Z0-9]{1,11}$)
3. Validate all recipients via CamMobileValidator.validate() — collect ALL invalid ones, fail-fast on first or collect all?
   → COLLECT ALL: v8 contract says "if any recipient invalid → entire request rejected"
   → Return 400 with all invalid numbers for better UX (can enumerate them all at once)
4. Validate message not blank
5. Validate scheduleTime (if present, must be in the future UTC)
6. Calculate segment count
7. Check balance >= totalSegments (throw InsufficientBalanceException if not)
8. Only after ALL validation passes: reserve credits
9. Persist SendRequest + SendRequestRecipient rows
10. Return 200 ACCEPTED
```

**Why this order matters:** Credit reservation is the only irreversible step. All checks must pass before reserve() is called. If reserve() succeeds and then persistence fails, the transaction rolls back and the reservation is released automatically (same @Transactional scope).

### Pattern 2: Idempotency via UNIQUE Constraint + Lookup

```
sendRequestId uniqueness is enforced at two levels:

1. Database: UNIQUE (client_id, send_request_id) on send_request table
2. Application: Before any processing, query for existing SendRequest row:
   Optional<SendRequest> existing = sendRequestRepo.findByClientIdAndSendRequestId(clientId, sendRequestId)
   if (existing.isPresent()) {
       return toSendSmsResponse(existing.get());  // return original response, no re-charge
   }
```

Do NOT rely solely on DataIntegrityViolationException for idempotency — race conditions between the check and insert are acceptable at low volume (v1 scale), but the application-level check first prevents re-charging on exact duplicate requests. The UNIQUE constraint is the final safety net.

### Pattern 3: Segment Calculation (in-process utility, not a library)

GSM-7 vs UCS-2 detection and segment count:

```java
// In SmsService or a static utility
public static int calculateSegments(String message) {
    boolean isGsm7 = isAllGsm7(message);
    int length = message.length();
    if (isGsm7) {
        return length <= 160 ? 1 : (int) Math.ceil((double) length / 153);
    } else {
        return length <= 70 ? 1 : (int) Math.ceil((double) length / 67);
    }
}

private static final Set<Character> GSM7_CHARS = Set.of(
    // Standard GSM-7 character set: A-Z, a-z, 0-9, and common punctuation
    // See GSM 03.38 spec
);

private static boolean isAllGsm7(String message) {
    return message.chars().allMatch(c -> GSM7_CHARS.contains((char) c));
}
```

Credits reserved = `segmentsPerMessage * recipientCount`. The response reports `calculated_segment_count` = segmentsPerMessage. Phase 4 will debit based on provider-confirmed segment count (from Nexah's `total_sms_unit`).

### Pattern 4: Scheduled SMS Poller with @Scheduled

Spring's `@Scheduled` annotation on a method in a `@Service` bean is sufficient for v1. No Spring Batch, no Quartz.

```java
// In SmsSchedulerService:
@Scheduled(fixedDelay = 30_000)   // run every 30s after last completion
@Transactional
public void dispatchScheduledMessages() {
    Instant now = Instant.now();
    List<SendRequest> due = sendRequestRepository.findDueScheduledRequests(now);
    for (SendRequest request : due) {
        try {
            // Phase 4: submit to Nexah provider
            // For Phase 3, just mark SUBMITTED (provider submission is Phase 4's job)
            request.setStatus(SendRequestStatus.SUBMITTED);
            sendRequestRepository.save(request);
        } catch (Exception e) {
            log.error("Failed to dispatch scheduled request {}", request.getSendRequestId(), e);
        }
    }
}
```

The repository query: `SELECT s FROM SendRequest s WHERE s.status = 'ACCEPTED' AND s.scheduleTime IS NOT NULL AND s.scheduleTime <= :now`

**Enabling @Scheduled:** Requires `@EnableScheduling` on a `@Configuration` class. Add to `ClientSecurityConfiguration` or a dedicated `ClientConfig` class.

### Pattern 5: Cancel Flow State Machine

The cancel endpoint is `DELETE /v1/sms/scheduled/{sendRequestId}`. Cancel is allowed only when `status = ACCEPTED` AND `scheduleTime IS NOT NULL` (it's a scheduled message) AND it has not yet been submitted.

```
State machine for SendRequest:

ACCEPTED ────────────────────────────────→ SUBMITTED  (Phase 4: provider submission)
    │                                          │
    └─→ CANCELLED (cancel endpoint)     COMPLETED / FAILED (Phase 4: delivery callbacks)
                                               │
                                          FINALIZED / FAIL_FINALIZED (Phase 4)
```

Cancel transition logic:

```java
// In SmsService:
@Transactional
public CancelSmsResponse cancelScheduled(Long clientId, String sendRequestId) {
    SendRequest request = sendRequestRepo.findByClientIdAndSendRequestId(clientId, sendRequestId)
        .orElseThrow(() -> new ResourceNotFoundException("Send request not found", "send_request"));

    if (request.getStatus() != SendRequestStatus.ACCEPTED || request.getScheduleTime() == null) {
        throw new CancelNotAllowedException("Cannot cancel: request is already " + request.getStatus());
    }

    creditReservationService.release(clientId, request.getReservationId());
    request.setStatus(SendRequestStatus.CANCELLED);
    sendRequestRepo.save(request);

    return new CancelSmsResponse(sendRequestId, "CANCELLED");
}
```

`request.getReservationId()` means `SendRequest` must store the `reservationId` returned by `CreditReservationService.reserve()`.

### Pattern 6: Rate Limiting Wire-Up (N-token, recipients/min)

The N-token overload was built in Phase 1 specifically for this endpoint.

```java
// In SmsResource.sendSms():
Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
int recipientCount = request.recipients().size();

// 1000 recipients/min per client (AUTH-05)
if (!rateLimitingService.tryConsume(
        String.valueOf(clientId),
        "sms_recipients",
        1000L,
        1L,
        TimeUnit.MINUTES,
        recipientCount)) {
    throw new RateLimitExceededException("Recipient rate limit exceeded");
}

// 10 requests/sec per client (handled by @RateLimited aspect from Phase 1 — if wired to this endpoint)
```

The `@RateLimited` AOP aspect from Phase 1 applies to `@RateLimited`-annotated methods. If `SmsResource.sendSms()` is annotated with `@RateLimited`, the 10 req/s limit is enforced by the aspect automatically. The N-token recipient check must be called explicitly.

### Pattern 7: LockTimeoutException Handler in ApiAdvice

The STATE.md pending todo: "Phase 3: Add @ExceptionHandler in ApiAdvice for LockTimeoutException → HTTP 503". This is needed because `CreditReservationService.reserve()` acquires a SELECT FOR UPDATE with 2000ms timeout. Under load, `LockTimeoutException` (Hibernate) propagates up and must be caught.

```java
// In ApiAdvice:
@ExceptionHandler(org.hibernate.exception.LockTimeoutException.class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public ErrorDto lockTimeoutHandler(final org.hibernate.exception.LockTimeoutException exception) {
    final String defaultMsg = "Server temporarily busy, please retry.";
    return logErrorAndReturnDTO(exception, defaultMsg, "PROVIDER_UNAVAILABLE");
}
```

Note: The exception class may be `jakarta.persistence.LockTimeoutException` (JPA spec) or `org.hibernate.exception.LockTimeoutException` (Hibernate). Check which is thrown by the `findByClientIdForUpdate` timeout and handle the correct class. The QueryHint `jakarta.persistence.lock.timeout` may cause `PessimisticLockingFailureException` from Spring's exception translation layer. Add handlers for all variants.

### Anti-Patterns to Avoid

- **Do not validate recipients after credit reservation.** The reservation must happen last among all pre-checks. Rolling back after reservation is safe (transaction), but it wastes a DB lock round-trip.
- **Do not use `@Lock` on the SendRequest lookup.** Only the credit balance row needs `SELECT FOR UPDATE`. The `sendRequestId` uniqueness check does not need pessimistic locking.
- **Do not calculate credits per-recipient separately.** Calculate total credits = `segments * recipientCount` as a single amount for a single `reserve()` call. This creates one reservation entry, one reservationId.
- **Do not use an event-driven scheduler for Phase 3.** @Scheduled is sufficient for v1 delivery at 30s latency. Avoid Spring Batch or Quartz unless sub-30s guarantees are needed.
- **Do not add a `CANCELLED` to `ACCEPTED` re-activation transition.** Cancel is terminal. No un-cancel.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SMS segment count | Custom formula from scratch | Static utility method with GSM 03.38 constants | Formula is ~10 lines; GSM-7 charset must be exact |
| Cameroon phone validation | Custom regex | `CamMobileValidator.validate()` (already exists) | Existing validator has operator-specific patterns (MTN/Orange/NextTel), handles country code stripping |
| Credit atomic reservation | Any new locking strategy | `CreditReservationService.reserve(clientId, amount, reference)` (already built in Phase 2) | SELECT FOR UPDATE with 2s timeout, InsufficientBalanceException, returns reservationId |
| Idempotency token check | Application-layer deduplication cache | UNIQUE (client_id, send_request_id) DB constraint + application pre-check query | DB constraint is crash-safe; in-memory cache loses state on restart |
| Scheduled job | Quartz, Spring Batch | Spring `@Scheduled(fixedDelay = 30_000)` | No coordinator, no external dependency, sufficient for v1 |
| Rate limit enforcement | Custom counter | `RateLimitingService.tryConsume(...)` N-token overload (already built in Phase 1) | Bucket4j greedy bucket, already wired with client_id identifier |

**Key insight:** CreditReservationService and RateLimitingService were built specifically to be called from this phase. Phase 3 is primarily assembly.

---

## Data Model

### Entity: SendRequest

```sql
-- V5__send_request.sql
CREATE TABLE main.send_request (
    id                  BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    send_request_id     VARCHAR(200) NOT NULL,     -- client-supplied idempotency key
    sender              VARCHAR(11) NOT NULL,      -- max 11 chars, A-Z 0-9
    message             TEXT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',   -- SendRequestStatus enum
    schedule_time       TIMESTAMP,                 -- NULL = immediate, NOT NULL = scheduled
    message_count       INT NOT NULL,              -- count of recipients
    segment_count       INT NOT NULL,              -- segments per message (estimated)
    reserved_credits    BIGINT NOT NULL,           -- = segment_count * message_count
    reservation_id      BIGINT NOT NULL,           -- CreditLedgerEntry.id from reserve()
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT,
    entity_status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_send_request_client_ref UNIQUE (client_id, send_request_id)
);
CREATE INDEX idx_send_request_client_status ON main.send_request(client_id, status);
CREATE INDEX idx_send_request_scheduled ON main.send_request(schedule_time, status)
    WHERE schedule_time IS NOT NULL AND status = 'ACCEPTED';   -- partial index for scheduler query
```

**Note on `status` column naming:** `AbstractAuditingEntity` already has a `status` column of type `EntityStatus` (ACTIVE/INACTIVE/DELETED). To avoid collision, use a different column name for the SMS lifecycle state. Two options:
- Use `send_status` or `request_status` as the column name for the lifecycle enum (map with `@Column(name = "send_status")`)
- Override the inherited `status` field — risky, complex

**Recommended:** Add a separate `sendStatus` field (type `SendRequestStatus`) mapped to column `send_status`. Keep the inherited `status = ACTIVE` to satisfy the audit entity. This is the same pattern used by `TopupRequestEntity` which has both inherited `status` and its own `topupStatus` field.

### Entity: SendRequestRecipient

```sql
CREATE TABLE main.send_request_recipient (
    id                  BIGINT PRIMARY KEY,
    send_request_id_fk  BIGINT NOT NULL REFERENCES main.send_request(id),  -- FK name avoids confusion with the string field
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    recipient           VARCHAR(20) NOT NULL,      -- E.164 phone number e.g. +237655123456
    send_status         VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',
    gateway_message_id  VARCHAR(100),              -- assigned at submission time (Phase 4)
    provider_message_id VARCHAR(200),              -- Nexah messageid (Phase 4)
    segments_consumed   INT,                       -- from Nexah total_sms_unit (Phase 4)
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_send_request_recipient_request ON main.send_request_recipient(send_request_id_fk);
CREATE INDEX idx_send_request_recipient_client ON main.send_request_recipient(client_id, send_status);
```

---

## Common Pitfalls

### Pitfall 1: Security Chain — SMS Endpoint Not Protected by API Key Auth

**What goes wrong:** `SmsResource` at `/v1/sms/**` is not inside `securityMatcher("/v1/api/**")` of `ClientSecurityConfiguration`. The @Order(2) JWT chain intercepts it, rejecting API key clients (who have `ROLE_API_CLIENT`, not `ROLE_USER/ADMIN`), resulting in 403 for all SMS send requests.

**Why it happens:** `ClientSecurityConfiguration.securityMatcher("/v1/api/**")` is scoped to only `/v1/api/**`. Credit/topup/SMS endpoints at `/v1/credits/**` and `/v1/sms/**` fall to the JWT chain.

**How to avoid:** The Phase 3 plan must update `ClientSecurityConfiguration` to widen its `securityMatcher` to cover all client-facing endpoints. Two options:
- Option A (simpler): Change `securityMatcher("/v1/api/**")` to `securityMatcher("/v1/**")` — this claims all `/v1/**` paths for API key auth, preventing the JWT chain from seeing them. Admin endpoints at `/api/admin/**` are unaffected (different path prefix).
- Option B (additive): Add a second API-key-protected chain at @Order(0) for `/v1/sms/**` and `/v1/credits/**`.

Option A is strongly preferred. It's a one-line change that correctly models the intent: all `/v1/**` paths are client-facing and require API key auth.

**Warning signs:** `403 Forbidden` on `POST /v1/sms/send` with valid API key. Also affects existing `/v1/credits/**` and `/v1/credits/topups/**` endpoints — these are currently unprotected from JWT clients with incorrect authorities.

### Pitfall 2: Status Column Name Collision with AbstractAuditingEntity

**What goes wrong:** `SendRequest extends AbstractAuditingEntity` inherits a `status` field (type `EntityStatus`). Adding another `status` field for the SMS lifecycle (type `SendRequestStatus`) produces a Hibernate mapping collision.

**Why it happens:** Both the parent class and subclass have a field that maps to `status` column.

**How to avoid:** Name the lifecycle status column `send_status` and annotate with `@Column(name = "send_status")`. Follow the exact pattern in `TopupRequestEntity` which has `topupStatus` mapped to `topup_status`. Verify `TopupRequestEntity` implementation for the exact Lombok/JPA annotations needed.

**Warning signs:** `Repeated column in mapping for entity: send_request column: status` Hibernate startup error.

### Pitfall 3: Partial Send When Transaction Rolls Back After Reserve

**What goes wrong:** `CreditReservationService.reserve()` succeeds, then `SendRequest` persistence fails (e.g., DB error). Credits are reserved but no `SendRequest` row exists — the client cannot cancel, and the credits are stuck.

**Why it happens:** If reserve() and SendRequest persistence are in different transactions, they are not atomic.

**How to avoid:** The entire `SmsService.sendSms()` method must be `@Transactional`. `CreditReservationService` is also `@Transactional` — when called from within `SmsService`'s transaction, it participates in the same transaction (Spring's default propagation `REQUIRED`). If the outer transaction rolls back, the reservation ledger entry is also rolled back. This is correct.

**Warning signs:** Client has reduced balance but no `SendRequest` row in DB. Credits appear reserved but no idempotency key exists.

### Pitfall 4: DUPLICATE_SEND_REQUEST_ID Returns 200 (Idempotent) vs 409 (Error)

**What goes wrong:** The v8 contract says:
- Section 7.4: duplicate `sendRequestId` → return original response
- Section 3 HTTP codes table: `Duplicate sendRequestId → 409 Conflict`
- Section 4 error example: `HTTP 409, error_code: "DUPLICATE_SEND_REQUEST_ID"`

These sections contradict each other. Section 7.4 (idempotency) is the domain behavior specification; the HTTP code table is supplementary. The intent is:
- If the caller retries with the same `sendRequestId` → 200 with original response (idempotent retry)
- If a DIFFERENT request reuses a `sendRequestId` — there is no way to distinguish this case from a retry at the API level

**Resolution:** Return 200 with original response for any duplicate `sendRequestId`. The v8 contract section 7.4 wins over the HTTP code table. Do NOT return 409 for idempotent retries — this would break client retry logic.

**Warning signs:** Client retry logic fails because 409 is not being caught as a successful outcome.

### Pitfall 5: scheduleTime in Client's Local Time vs UTC

**What goes wrong:** Client submits `scheduleTime: "2026-03-04T10:00:00"` (no Z suffix). The system interprets it differently on different JVM timezones.

**How to avoid:** Validate that `scheduleTime` is a valid ISO-8601 instant (has Z or +00:00 offset). In the DTO, use `Instant` type — Jackson deserialization of `Instant` requires the Z suffix or timezone offset. Without it, Jackson throws `InvalidFormatException` which becomes HTTP 400 automatically.

**Warning signs:** `scheduleTime` is accepted but fires at wrong time.

### Pitfall 6: @Scheduled Does Not Trigger Without @EnableScheduling

**What goes wrong:** `SmsSchedulerService.@Scheduled` method compiles fine but never executes. No error in logs.

**Why it happens:** `@EnableScheduling` must be on a `@Configuration` class in the application context.

**How to avoid:** Add `@EnableScheduling` to an existing `@Configuration` class (e.g., `ClientSecurityConfiguration` or a new `ClientConfig`). Verify by checking logs for the scheduler thread startup message.

**Warning signs:** Scheduled SMS messages stay in `ACCEPTED` state indefinitely.

### Pitfall 7: N-token Rate Limit Bucket Key Mismatch

**What goes wrong:** The N-token tryConsume for 1000 recipients/min uses a different bucket key than the 10 req/s limit, creating two separate buckets. This is correct. However, if the limitKey is hardcoded to a different string in the aspect vs the controller, the two limits may interfere.

**How to avoid:** The N-token call in the controller explicitly passes `limitKey = "sms_recipients"`. The `@RateLimited` aspect uses its own bucket key derived from the annotation parameters. These are different bucket keys by design — the two limits are independent. Confirm the RateLimitingAspect does NOT consume tokens from the "sms_recipients" bucket.

---

## Code Examples

### SendRequest JPA Entity (skeleton)

```java
// Source: codebase pattern from TopupRequestEntity, ClientEntity
@Entity
@Table(name = "send_request", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SendRequest extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "send_request_id", nullable = false, length = 200)
    private String sendRequestId;

    @Column(name = "sender", nullable = false, length = 11)
    private String sender;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false, length = 30)
    private SendRequestStatus sendStatus;

    @Column(name = "schedule_time")
    private Instant scheduleTime;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "segment_count", nullable = false)
    private int segmentCount;

    @Column(name = "reserved_credits", nullable = false)
    private long reservedCredits;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;   // CreditLedgerEntry.id from CreditReservationService.reserve()

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
```

### SmsService.sendSms() — orchestration skeleton

```java
// Source: codebase patterns; CreditReservationService API from Phase 2
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final SendRequestRepository sendRequestRepo;
    private final SendRequestRecipientRepository recipientRepo;
    private final CreditReservationService creditReservationService;
    private final RateLimitingService rateLimitingService;

    public SendSmsResponse sendSms(Long clientId, SendSmsRequest request) {
        // 1. Idempotency check
        Optional<SendRequest> existing = sendRequestRepo.findByClientIdAndSendRequestId(clientId, request.sendRequestId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // 2. Rate limit: N-token for recipients/min (AUTH-05)
        int recipientCount = request.recipients().size();
        if (!rateLimitingService.tryConsume(String.valueOf(clientId), "sms_recipients",
                1000L, 1L, TimeUnit.MINUTES, recipientCount)) {
            throw new RateLimitExceededException("Recipient rate limit exceeded");
        }

        // 3. Validate sender ID
        validateSenderId(request.sender());

        // 4. Validate all recipients — collect failures
        List<String> invalidRecipients = request.recipients().stream()
            .filter(r -> !isValidCamRecipient(r))
            .toList();
        if (!invalidRecipients.isEmpty()) {
            throw new InvalidPhoneNumberException("Invalid recipients: " + invalidRecipients);
        }

        // 5. Validate scheduleTime
        if (request.scheduleTime() != null && !request.scheduleTime().isAfter(Instant.now())) {
            throw new InvalidScheduleTimeException("scheduleTime must be in the future");
        }

        // 6. Validate message
        if (request.message() == null || request.message().isBlank()) {
            throw new InvalidRequestException("message is required");
        }

        // 7. Calculate segments
        int segmentCount = SmsSegmentCalculator.calculate(request.message());
        long totalCredits = (long) segmentCount * recipientCount;

        // 8. Reserve credits (throws InsufficientBalanceException if balance insufficient)
        String reference = "sms:" + request.sendRequestId();
        long reservationId = creditReservationService.reserve(clientId, totalCredits, reference);

        // 9. Persist (within same @Transactional — if this fails, reserve() rolls back too)
        SendRequest sendRequest = SendRequest.builder()
            .clientId(clientId)
            .sendRequestId(request.sendRequestId())
            .sender(request.sender())
            .message(request.message())
            .sendStatus(SendRequestStatus.ACCEPTED)
            .scheduleTime(request.scheduleTime())
            .messageCount(recipientCount)
            .segmentCount(segmentCount)
            .reservedCredits(totalCredits)
            .reservationId(reservationId)
            .status(EntityStatus.ACTIVE)
            .build();
        sendRequestRepo.save(sendRequest);

        request.recipients().forEach(phone ->
            recipientRepo.save(SendRequestRecipient.builder()
                .sendRequestIdFk(sendRequest.getId())
                .clientId(clientId)
                .recipient(phone)
                .sendStatus(SendRequestStatus.ACCEPTED)
                .status(EntityStatus.ACTIVE)
                .build())
        );

        // 10. Get balance after reservation for response
        // NOTE: CreditService.getBalance is a non-locking read — safe to call here
        // Balance was already reduced by reserve()

        return toResponse(sendRequest);
    }
}
```

### Sender ID Validation (inline, no library)

```java
private static final Pattern SENDER_ID_PATTERN = Pattern.compile("^[A-Z0-9]{1,11}$");

private void validateSenderId(String sender) {
    if (sender == null || !SENDER_ID_PATTERN.matcher(sender).matches()) {
        throw new InvalidSenderIdException("Sender ID must be 1-11 characters, A-Z and 0-9 only");
    }
}
```

Source: v8 contract Section 8 — max 11 chars, A-Z 0-9, uppercase recommended.

### Scheduled Dispatch Poller

```java
// Source: Spring @Scheduled documentation pattern — standard Spring Boot feature
@Scheduled(fixedDelay = 30_000)
@Transactional
public void dispatchScheduledMessages() {
    Instant now = Instant.now();
    List<SendRequest> due = sendRequestRepository.findDueScheduledRequests(now);
    if (due.isEmpty()) return;
    log.info("Dispatching {} due scheduled SMS requests", due.size());
    for (SendRequest request : due) {
        // Phase 3: mark as SUBMITTED (provider submission is Phase 4)
        // Phase 4 will replace this with actual Nexah API call
        request.setSendStatus(SendRequestStatus.SUBMITTED);
        sendRequestRepository.save(request);
    }
}

// In SendRequestRepository:
@Query("SELECT s FROM SendRequest s WHERE s.sendStatus = 'ACCEPTED' " +
       "AND s.scheduleTime IS NOT NULL AND s.scheduleTime <= :now")
List<SendRequest> findDueScheduledRequests(@Param("now") Instant now);
```

### v8 Contract Response Records

```java
// POST /v1/sms/send — 200 OK
public record SendSmsResponse(
    @JsonProperty("request_id") String requestId,          // gateway internal ID: "req_" + sendRequest.getId()
    @JsonProperty("sendRequestId") String sendRequestId,   // client-supplied idempotency key
    @JsonProperty("message_count") int messageCount,
    @JsonProperty("calculated_segment_count") int calculatedSegmentCount,
    @JsonProperty("reserved_credits") long reservedCredits,
    @JsonProperty("available_balance_after_reservation") long availableBalanceAfterReservation,
    String status                                          // "ACCEPTED"
) {}

// DELETE /v1/sms/scheduled/{sendRequestId} — 200 OK
public record CancelSmsResponse(
    @JsonProperty("sendRequestId") String sendRequestId,
    String status   // "CANCELLED"
) {}

// GET /v1/sms/status/{sendRequestId} — 200 OK (Phase 3 stub; extended in Phase 4)
public record MessageStatusResponse(
    @JsonProperty("sendRequestId") String sendRequestId,
    @JsonProperty("overall_status") String overallStatus,
    int page,
    @JsonProperty("page_size") int pageSize,
    @JsonProperty("total_messages") long totalMessages,
    List<MessageStatusEntry> messages
) {}

public record MessageStatusEntry(
    String recipient,
    String state,
    @JsonProperty("gateway_message_id") String gatewayMessageId,
    @JsonProperty("provider_message_id") String providerId,
    @JsonProperty("segments_consumed") Integer segmentsConsumed
) {}
```

---

## API Contract Alignment

### Client-Facing Endpoints (API key auth, all under `/v1/**`)

| Method | Path | Controller | Requirement | Status |
|--------|------|-----------|-------------|--------|
| POST | `/v1/sms/send` | `SmsResource.sendSms()` | SMS-01, SMS-02, SMS-03, SMS-06 | Plan 03-01, 03-02 |
| DELETE | `/v1/sms/scheduled/{sendRequestId}` | `SmsResource.cancelScheduled()` | SMS-05 | Plan 03-03 |
| GET | `/v1/sms/status/{sendRequestId}` | `SmsResource.getStatus()` | (Phase 4 detailed status; stub in 03) | Plan 03-01 |

### HTTP Status Codes (v8 contract alignment)

| Scenario | Status | Error Code |
|----------|--------|-----------|
| SMS accepted | 200 OK | — |
| Duplicate sendRequestId (retry) | 200 OK | — (returns original response) |
| Invalid recipient phone number | 400 Bad Request | `INVALID_PHONE_NUMBER` |
| Insufficient balance | 400 Bad Request | `INSUFFICIENT_CLIENT_BALANCE` |
| Invalid sender ID | 400 Bad Request | `INVALID_SENDER_ID` |
| Invalid schedule time | 400 Bad Request | `INVALID_SCHEDULE_TIME` |
| Rate limit exceeded | 429 Too Many Requests | `RATE_LIMIT_EXCEEDED` |
| Lock timeout (concurrent send) | 503 Service Unavailable | `PROVIDER_UNAVAILABLE` |
| Cancel: request not in cancellable state | 409 Conflict | `CANCEL_NOT_ALLOWED` (v8 does not specify; use 409) |

---

## CreditReservationService Contract (Phase 2 — verified from codebase)

The exact method signatures callable by Phase 3:

```java
// From CreditReservationService (src/main/java/com/softropic/sendam/client/service/CreditReservationService.java)

long reserve(Long clientId, long amount, String reference)
// - Acquires SELECT FOR UPDATE on client_credit_balance
// - Throws InsufficientBalanceException if balance < amount
// - Writes SMS_RESERVATION ledger entry (amount = -N)
// - Returns ledger entry id (reservationId) — store on SendRequest entity
// - amount must be positive; throws IllegalArgumentException if not

void release(Long clientId, Long reservationId)
// - Loads the original SMS_RESERVATION entry by reservationId
// - Verifies clientId matches (throws ResourceNotFoundException if not)
// - Acquires SELECT FOR UPDATE on client_credit_balance
// - Writes SMS_REFUND entry (amount = +N)
// - Restores balance
// - Called by cancel endpoint (Phase 3) and by Phase 4 on provider failure

void debit(Long clientId, Long reservationId, long actualAmount)
// - Loads the original SMS_RESERVATION entry by reservationId
// - Acquires SELECT FOR UPDATE on client_credit_balance
// - Writes SMS_DEBIT entry (amount = -actualAmount)
// - If actualAmount < reservedAmount: also writes SMS_REFUND for over-reservation
// - Called by Phase 4 after provider confirms segment count (Nexah total_sms_unit)
```

---

## What Phase 4 Needs from Phase 3

Phase 4 (Provider Integration) takes over from Phase 3 at the point of submitting to Nexah. Phase 4 requires:

| Phase 3 Artifact | What Phase 4 Uses | Why |
|------------------|-------------------|-----|
| `SendRequest` entity with `reservation_id` | `CreditReservationService.debit(clientId, reservationId, actualAmount)` | Phase 4 calls debit after Nexah confirms `total_sms_unit`; needs `reservationId` from the row |
| `SendRequest` with `send_status = ACCEPTED` and `schedule_time IS NOT NULL` | Scheduler query `findDueScheduledRequests(now)` | Phase 4 submits due scheduled messages |
| `SendRequest` with `send_status = SUBMITTED` (immediate) | Phase 4 submits immediately on accept (no scheduler needed for immediate sends) | Phase 3's scheduler handles delayed delivery; immediate sends go straight to SUBMITTED |
| `SendRequestRecipient` rows with `recipient` (E.164 format) | Nexah API `mobiles` field: comma-separated E.164 numbers | Phase 4 builds the Nexah request from recipient rows |
| `SendRequestRecipient.gateway_message_id` and `provider_message_id` columns | Phase 4 writes Nexah's `messageid` and `smsclientid` here | Delivery report matching (Phase 5) needs these IDs |
| `SendRequestStatus` enum with all states | Phase 4 transitions ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED | State machine must be complete |

**Critical for Phase 4:** Immediate (non-scheduled) sends should be submitted to Nexah synchronously within `SmsService.sendSms()` in Phase 4, not via the scheduler. Phase 3's scheduler is only for scheduled (future) messages. Phase 3 should mark immediate sends as `ACCEPTED` and Phase 4 will wire the actual Nexah call synchronously before returning.

**For Phase 3 specifically:** The scheduler stub marks due scheduled requests as `SUBMITTED` without calling Nexah. Phase 4 replaces this with the actual Nexah HTTP call.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Optimistic retry for concurrent sends | `SELECT FOR UPDATE` (pessimistic) via `CreditReservationService` (already built) | No balance overspend; deterministic |
| Phone number validation with regex | `CamMobileValidator.validate()` (already built, operator-specific) | Handles MTN/Orange/NextTel patterns correctly |
| Mutable balance column | Ledger-first with reservation/debit/refund entries (already built in Phase 2) | Full audit trail; crash-safe |
| Separate scheduler daemon | Spring `@Scheduled` | Zero operational overhead; sufficient for v1 |

---

## Open Questions

1. **Security chain matcher scope**
   - What we know: `ClientSecurityConfiguration.securityMatcher("/v1/api/**")` only covers `/v1/api/**`. Existing `CreditResource` (`/v1/credits/**`) and `TopupResource` (`/v1/credits/topups/**`) fall to the JWT chain, which has different authority requirements.
   - What's unclear: Are the existing `/v1/credits/**` endpoints actually functional for API key clients in the current state? If so, the @Order(2) JWT chain must somehow permit `ROLE_API_CLIENT`. If not, this is a pre-existing bug.
   - Recommendation: Plan 03-01 must include widening `ClientSecurityConfiguration.securityMatcher` from `/v1/api/**` to `/v1/**`. This is a one-line change that fixes both the SMS endpoint and any existing gap in credit/topup security.

2. **Immediate sends: synchronous Nexah call or scheduler?**
   - What we know: v8 contract says "Messages enter state `ACCEPTED`" on send response. Provider submission happens after acceptance. Phase 4 owns provider submission.
   - What's unclear: Phase 3 plan says scheduler handles dispatch. But for immediate (non-scheduled) sends, waiting up to 30s for the scheduler is poor UX.
   - Recommendation: Phase 3 accepts and reserves only. Phase 4 adds immediate synchronous Nexah submission for non-scheduled sends within the sendSms() flow, AND the scheduler for scheduled messages. Phase 3 should leave the SUBMITTED transition for Phase 4.

3. **GSM-7 character set completeness**
   - What we know: GSM 03.38 defines a specific 128-character basic set plus an extension table.
   - What's unclear: Whether the project needs the extension table (characters like `{`, `}`, `[`, `]`, `~`, `|`, `\`, `€` which are 2 GSM units each).
   - Recommendation: For v1, use a standard GSM-7 basic set constant. If any character in the message is outside it, use UCS-2. Extension characters are uncommon in Cameroon business SMS context. LOW confidence — acceptable for v1 billing estimation.

---

## Sources

### Primary (HIGH confidence)
- Codebase (verified directly):
  - `CreditReservationService.java` — reserve/release/debit method signatures and contracts
  - `RateLimitingService.java` — N-token tryConsume overload (implemented in Phase 1)
  - `CamMobileValidator.java` — validate() method signature, InvalidMobileNumberException
  - `ApiAdvice.java` — existing handlers, pattern for adding new @ExceptionHandler
  - `ClientSecurityConfiguration.java` — securityMatcher scope issue
  - `AppEndpoints.java` — CLIENT_API = "/v1/api/**" confirms the scope discrepancy
  - `AbstractAuditingEntity.java` — inherited status field (conflict detection)
  - `TopupRequestEntity.java` — pattern for adding separate lifecycle status alongside inherited status
  - `requirements/client-facing_API_contract_v8.md` — complete v8 API spec for all SMS endpoints
  - `requirements/nexahApi.md` — Nexah response fields (for Phase 4 handoff requirements)
- `.planning/STATE.md` — pending todos (LockTimeoutException handler, N-token wire-up)

### Secondary (MEDIUM confidence)
- Spring `@Scheduled` documentation — fixedDelay semantics, @EnableScheduling requirement; verified against known Spring Boot 3.x behavior
- SMS segment calculation formula (GSM 03.38) — standard telecom spec, widely documented

### Tertiary (LOW confidence)
- GSM-7 character set completeness for extension table handling — training knowledge, not verified against official spec document

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in pom.xml; no new dependencies
- CreditReservationService contract: HIGH — verified from actual source file
- Security chain wiring issue: HIGH — verified from ClientSecurityConfiguration and AppEndpoints
- Data model (SendRequest entity): HIGH — derived from v8 contract + established codebase patterns
- Scheduler (@Scheduled): MEDIUM — Spring Boot feature, pattern confirmed from training but not directly tested in codebase
- GSM-7 segment calculation: MEDIUM — formula from v8 contract, charset composition LOW

**Research date:** 2026-03-10
**Valid until:** 2026-06-10 (Spring Boot 3.x @Scheduled, JPA patterns, Bucket4j API are all stable)
