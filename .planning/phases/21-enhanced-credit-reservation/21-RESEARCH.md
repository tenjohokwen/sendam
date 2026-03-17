# Phase 21: Enhanced Credit Reservation — Research

**Researched:** 2026-03-17
**Domain:** SMS segment calculation, credit reservation enhancement, schema migration, per-recipient data storage
**Confidence:** HIGH

---

## Summary

Phase 21 stores per-recipient expected segment counts at reservation time so the Phase 22 booking step can detect deviations. The work is purely additive: no existing behaviour changes, no new service classes are needed, only schema additions and targeted changes to `SmsService` and `CreditReservationService`.

The `SmsSegmentCalculator` already implements the correct GSM-7/UCS-2 formula. It is currently `package-private` in `gateway.sms.service` and operates at the send-request level (one segment count for all recipients). Phase 21 keeps that formula but also stores the result per recipient (on `send_request_recipient`) and stores the buffered vs. raw reservation amounts on `send_request`.

The standard pattern for schema additions in this project is a new numbered Flyway migration (`V13__...`). Next available version is 13. The entity changes are pure additions (new `@Column` fields on existing JPA entities), which is the established pattern used by every prior migration.

**Primary recommendation:** One Flyway migration (`V13__enhanced_reservation.sql`) adds two columns to `send_request` and one column to `send_request_recipient`. `SmsService.sendSms()` is updated to apply the +1 buffer and populate all new fields. `CreditReservationService.reserve()` signature does not change — the buffer calculation belongs in `SmsService` where the business rules live, not in the reservation primitive.

---

## Standard Stack

### Core (all already in project)

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Flyway | bundled with Spring Boot 3.5.11 | Schema migration | Used for all 12 prior migrations; V13 is next |
| Spring Data JPA | bundled | ORM | All entities use `@SuperBuilder` + `AbstractAuditingEntity` |
| Lombok `@SuperBuilder` | bundled | Builder pattern | Required for entities inheriting `AbstractAuditingEntity` |
| JUnit 5 + Mockito | bundled | Unit testing | All existing service tests use `@ExtendWith(MockitoExtension.class)` |

### No New Dependencies

Phase 21 requires no new libraries. Everything needed exists in the codebase.

---

## Architecture Patterns

### Entity Field Addition Pattern

All entity additions in this project follow this exact pattern:
1. Add `@Column` annotated field with `nullable = false` or nullable depending on requirement.
2. Add Flyway migration that `ALTER TABLE ... ADD COLUMN` with a `DEFAULT` for existing rows.
3. Do NOT redeclare `status` — it lives in `AbstractAuditingEntity`; shadowing it breaks Hibernate silently (established pitfall from Phase 6).
4. Use `@Builder.Default` for fields with a constant default value.

### Segment Calculator — Package Visibility

`SmsSegmentCalculator` is currently `package-private` (`final class`, no `public`). It lives in `gateway.sms.service`. Since `SmsService` is in the same package, there is no access issue. Phase 21 does not need to make the calculator public.

### Reservation Amount Formula

Current formula in `SmsService.sendSms()`:
```java
int segmentCount = SmsSegmentCalculator.calculate(request.message());
long totalCredits = (long) segmentCount * recipientCount;
```

Phase 21 formula:
- `expectedSegments` = `SmsSegmentCalculator.calculate(request.message())`  (same, per-recipient)
- `rawExpected` = `expectedSegments * recipientCount`  (RESV-03: stored without buffer)
- `reservationAmount` = `(expectedSegments + 1) * recipientCount`  (RESV-02: +1 per recipient buffer)

The +1 buffer is per-recipient (adds `recipientCount` extra credits total), not a flat +1 on the total.

### Per-Recipient Storage

`SendRequestRecipient` already has a `segments_consumed INT` column (written by Phase 4 at booking time). Phase 21 adds a parallel `expected_segments INT NOT NULL` column written at reservation time. This is the per-recipient structured data required by RESV-04.

### SmsService Change Scope

Only `sendSms()` changes. The new logic is:
1. Calculate `expectedSegments` (same as before, no formula change)
2. Calculate `rawExpected = expectedSegments * recipientCount`
3. Calculate `reservationAmount = (expectedSegments + 1) * recipientCount`
4. Reserve `reservationAmount` credits (was `totalCredits = expectedSegments * recipientCount`)
5. Persist `SendRequest` with two new fields: `rawExpectedCredits` and `reservationAmount` (which replaces the old `reservedCredits` field — or `reservedCredits` is kept as the buffered amount and `rawExpectedCredits` is the new field)
6. Persist each `SendRequestRecipient` with `expectedSegments` set

**Key decision needed by planner:** `reservedCredits` on `SendRequest` already holds the reserved amount. After Phase 21, `reservedCredits` should hold the buffered amount (what was actually reserved). A new column `raw_expected_credits BIGINT NOT NULL` stores the unbuffered expected amount. This is consistent and non-breaking for existing downstream consumers of `reservedCredits`.

### Idempotency Path

The idempotent early-return in `sendSms()` reads the existing `SendRequest` and returns `toResponse()`. It does NOT re-execute the reservation path, so it will naturally return whatever was stored at first-submission time. No change needed to the idempotency path.

### CreditReservationService Signature

`reserve(Long clientId, long amount, String reference)` signature does NOT change. The buffered amount is passed in as `amount`. `CreditReservationService` is a generic credit primitive; the buffer business rule belongs in `SmsService`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SMS segment formula | Custom logic | Existing `SmsSegmentCalculator` | Already correct, tested, handles GSM-7/UCS-2 |
| DB migration | Manual schema scripts | Flyway `V13__...` in `src/main/resources/db/migration/` | All 12 prior migrations use this; Flyway runs on startup |
| Entity builder | Manual constructors | Lombok `@SuperBuilder` with `@Builder.Default` | Required for inheritance chain; all entities use this |

---

## Common Pitfalls

### Pitfall 1: Re-declaring `status` in Child Entity

**What goes wrong:** Hibernate silently fails to hydrate the `status` field if it is declared in both parent (`AbstractAuditingEntity`) and child.
**Why it happens:** JPA field access maps to the parent `@Column`; the child declaration shadows it.
**How to avoid:** Never declare a `status` field in entities extending `AbstractAuditingEntity`. Use `@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE;` only — and only if needed, since the parent already declares it with `INACTIVE` default.
**Warning signs:** `SendRequest` correctly uses `@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE;` without redeclaring `@Column`. Follow this exact pattern.

### Pitfall 2: Wrong Buffer Formula

**What goes wrong:** Adding +1 to the total instead of +1 per recipient.
**Why it happens:** Requirement says "+1 buffer per recipient" which means `(segmentCount + 1) * recipientCount`, not `segmentCount * recipientCount + 1`.
**How to avoid:** Write the formula as `(expectedSegments + 1L) * recipientCount`. Verify with unit test: 3 recipients, 2 expected segments → reservation = (2+1)*3 = 9, rawExpected = 2*3 = 6.

### Pitfall 3: Flyway Migration Uses ADD COLUMN Without DEFAULT

**What goes wrong:** Migration fails on a non-empty database if `NOT NULL` column added without DEFAULT.
**Why it happens:** PostgreSQL rejects `ADD COLUMN col NOT NULL` on existing rows with no default.
**How to avoid:** Use `ADD COLUMN col BIGINT NOT NULL DEFAULT 0` or equivalent. The column will be backfilled with the default for all existing rows. This is the pattern used by V12 (`ADD COLUMN frozen BOOLEAN NOT NULL DEFAULT FALSE`).

### Pitfall 4: SmsSegmentCalculator is Package-Private

**What goes wrong:** If `expectedSegments` calculation is needed in a different package (e.g., a utility class or test in a different package), the call fails to compile.
**Why it happens:** `SmsSegmentCalculator` is `package-private` (`final class` with no access modifier on the class declaration).
**How to avoid:** All Phase 21 logic stays inside `gateway.sms.service`. Both `SmsService` and any helper code are in the same package. Test class for `SmsService` is also in `gateway.sms.service` (test mirror). Do not move the calculator.

### Pitfall 5: `segmentCount` on `SendRequest` vs `expectedSegments` per Recipient

**What goes wrong:** Confusing the existing `segmentCount` field on `SendRequest` (which stores the per-message segment count, not total) with the new per-recipient field.
**Why it happens:** Naming overlap. `SendRequest.segmentCount` = number of segments for the message text (encoding-only, not × recipients). The new field on `SendRequestRecipient` also stores the same per-message segment count, but scoped to each recipient row for future deviation use.
**How to avoid:** Name the new `SendRequestRecipient` field `expectedSegments` (or `expected_segments` in SQL). It stores the same integer as `SendRequest.segmentCount` — they are equal for all recipients in the same batch (same message text). The purpose is to store it where the Phase 22 booking step can access it per-recipient.

---

## Code Examples

### Current Reservation Code in SmsService (lines 146–168)

```java
// Source: SmsService.java — current Phase 20 state
// Step 7: Calculate segments and total credits to reserve
int segmentCount = SmsSegmentCalculator.calculate(request.message());
long totalCredits = (long) segmentCount * recipientCount;

// Step 8: Reserve credits
String reference = "sms:" + request.sendRequestId();
long reservationId = creditReservationService.reserve(clientId, totalCredits, reference);

// Step 9: Persist SendRequest row
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
```

### Required Schema Addition (V13)

```sql
-- V13__enhanced_reservation.sql
ALTER TABLE main.send_request
    ADD COLUMN raw_expected_credits BIGINT NOT NULL DEFAULT 0;

ALTER TABLE main.send_request_recipient
    ADD COLUMN expected_segments INT NOT NULL DEFAULT 0;
```

Two columns, two tables. `DEFAULT 0` backfills existing rows safely.

### Entity Field Additions

SendRequest — new field:
```java
@Column(name = "raw_expected_credits", nullable = false)
private long rawExpectedCredits;
```

SendRequestRecipient — new field:
```java
@Column(name = "expected_segments", nullable = false)
private int expectedSegments;
```

### Updated Reservation Logic in SmsService

```java
// Phase 21: buffer = +1 per recipient
int expectedSegments = SmsSegmentCalculator.calculate(request.message());
long rawExpectedCredits = (long) expectedSegments * recipientCount;
long reservationAmount  = (long)(expectedSegments + 1) * recipientCount;  // RESV-02

String reference = "sms:" + request.sendRequestId();
long reservationId = creditReservationService.reserve(clientId, reservationAmount, reference);

SendRequest sendRequest = SendRequest.builder()
        // ... existing fields ...
        .segmentCount(expectedSegments)
        .reservedCredits(reservationAmount)          // buffered amount
        .rawExpectedCredits(rawExpectedCredits)      // RESV-03: unbuffered
        .reservationId(reservationId)
        .build();

// Per-recipient rows — RESV-04: store expected_segments on each
request.recipients().forEach(phone ->
    recipientRepo.save(SendRequestRecipient.builder()
        .sendRequestIdFk(sendRequest.getId())
        .clientId(clientId)
        .recipient(phone)
        .sendStatus(SendRequestStatus.ACCEPTED)
        .expectedSegments(expectedSegments)          // RESV-04
        .status(EntityStatus.ACTIVE)
        .build())
);
```

---

## Schema State

### Existing `send_request` columns (from V5 + V6)

```
id, client_id, send_request_id, sender, message, send_status, schedule_time,
message_count, segment_count, reserved_credits, reservation_id, finalized_at,
status, created_by, created_date, last_modified_by, last_modified_date, request_id, session_id
```

Phase 21 adds: `raw_expected_credits BIGINT NOT NULL DEFAULT 0`

### Existing `send_request_recipient` columns (from V5)

```
id, send_request_id_fk, client_id, recipient, send_status, gateway_message_id,
provider_message_id, segments_consumed, status, created_by, created_date,
last_modified_by, last_modified_date, request_id, session_id
```

Phase 21 adds: `expected_segments INT NOT NULL DEFAULT 0`

Note: `segments_consumed` (written by Phase 4/booking) and `expected_segments` (written by Phase 21/reservation) are parallel columns — consumed is actual, expected is pre-send estimate. Phase 22 will compare them.

---

## Impact on `debit()` / `release()` in CreditReservationService

`debit()` currently enforces `actualAmount <= reservedAmount`. After Phase 21, `reservedAmount` includes the +1 buffer per recipient. The Nexah-reported `total_sms_unit` is used as `actualAmount`. This means:

- For a single-recipient, 2-segment message: `reservedAmount = 3`, `actualAmount` from Nexah will typically be 2. Over-reservation of 1 is the buffer. This triggers the existing `overReservation > 0` refund path in `debit()`.
- The buffer is designed so `actualAmount > reservedAmount` (which throws `IllegalArgumentException`) should rarely or never occur.
- `debit()` logic is unchanged by Phase 21.

---

## Open Questions

1. **`SendSmsResponse` carries `reservedCredits`** — should it reflect the buffered or unbuffered amount?
   - What we know: `toResponse()` reads `req.getReservedCredits()`. After Phase 21, `reservedCredits` = buffered amount.
   - What's unclear: Whether the client-facing response should expose the buffer. The v8 API contract may specify this.
   - Recommendation: Check `requirements/client-facing_API_contract_v8.md`. If `reservedCredits` in the response is a documented field, expose the actual reserved (buffered) amount since that's what affects the client's balance. Planner should verify.

2. **Index on `expected_segments`?**
   - What we know: Phase 22 will query `send_request_recipient` by `send_request_id_fk` and read `expected_segments` alongside `segments_consumed`.
   - What's unclear: Whether the existing index `idx_send_request_recipient_request ON send_request_recipient(send_request_id_fk)` covers Phase 22 queries adequately.
   - Recommendation: No new index needed in Phase 21. Phase 22 research should confirm.

---

## Sources

### Primary (HIGH confidence)

- Direct code inspection of `CreditReservationService.java` — full reservation/debit/release logic
- Direct code inspection of `SmsService.java` — reservation call site and segment calculation
- Direct code inspection of `SmsSegmentCalculator.java` — GSM-7/UCS-2 formula
- Direct code inspection of `SendRequest.java` — entity fields
- Direct code inspection of `SendRequestRecipient.java` — entity fields including existing `segments_consumed`
- Direct code inspection of `V5__send_request.sql` through `V12__account_freeze.sql` — migration pattern
- Direct code inspection of `nexahApi.md` — `total_sms_unit` per-recipient field confirmed in both send response and DR callback
- Direct code inspection of `AbstractAuditingEntity.java` — inheritance and `status` field pattern
- Direct code inspection of `STATE.md` — accumulated decisions, next Flyway version is 13

### Tertiary (LOW confidence)

- None — all findings are from direct code inspection of this repository.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all existing, verified in source files
- Architecture: HIGH — verified against 12 prior migrations and entity patterns
- Pitfalls: HIGH — items 1 and 3 are explicitly documented as past bugs in STATE.md and ARCHITECTURE.md; others derived from direct inspection
- Formula: HIGH — RESV-02 is unambiguous in the requirement text; verified `(segmentCount+1)*recipientCount` formula

**Research date:** 2026-03-17
**Valid until:** This research is tied to specific source files; valid until those files change.
