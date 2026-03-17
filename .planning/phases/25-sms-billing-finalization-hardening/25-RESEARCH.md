# Phase 25: SMS Billing Finalization Hardening — Research

**Researched:** 2026-03-17
**Domain:** Spring Boot event-driven billing, credit reservation lifecycle, Java type safety
**Confidence:** HIGH — all findings based on direct codebase inspection

---

## Summary

Phase 25 closes three independent tech debt items identified in the v1.3 milestone audit. Each fix
is isolated: one functional bug (reservation leak in stale-SMS path), one latent type-safety hazard
(boxed Long in event record), and one comment-only error (Javadoc HTTP status). No new abstractions
are needed. All three can be done in small, surgical edits to existing files.

The dominant fix is the credit reservation leak: `SmsSchedulerService.forceFinalize()` marks stale
requests as `FAIL_FINALIZED` and saves them directly to the database, but never triggers billing
finalization. The `SmsFinalisedBillingListener` (and thus `FinalBookingService`) is never invoked
for timed-out SMS. The reservation amount remains subtracted from the client's balance indefinitely.

The clean fix is to use `ApplicationEventPublisher` to publish `SmsFinalisedEvent(actualSegments=0)`
from `forceFinalize()`. `FinalBookingService.book()` already handles the zero-segments case: it calls
`CreditReservationService.release()` and returns. The event-based approach is consistent with how
`SmsProviderReportListener` handles normal DLR completions.

**Primary recommendation:** Publish `SmsFinalisedEvent` from `forceFinalize()` with `actualSegments=0`
to release reservations; change `SmsFinalisedEvent.actualSegments` from `Long` to `long`; fix the
Javadoc in `InsufficientPlatformBalanceException`.

---

## Standard Stack

No new libraries required. All fixes use existing project infrastructure.

### Relevant Existing Services

| Class | Location | Role in Phase 25 |
|-------|----------|-----------------|
| `SmsSchedulerService` | `gateway.sms.service` | Fix: must publish event from `forceFinalize()` |
| `SmsFinalisedEvent` | `gateway.sms.contract` | Fix: change `Long actualSegments` → `long actualSegments` |
| `InsufficientPlatformBalanceException` | `gateway.billing.contract` | Fix: Javadoc typo only |
| `ApplicationEventPublisher` | Spring Framework | Already used by `SmsProviderReportListener` — inject into `SmsSchedulerService` |
| `CreditReservationService.release()` | `gateway.billing.service` | Already correct — just never called for stale path |
| `FinalBookingService.book()` | `gateway.billing.service` | Already handles `actualSegments=0` correctly (calls `release()`) |

---

## Architecture Patterns

### How Normal DLR Finalization Works (the pattern to follow)

`SmsProviderReportListener` receives `ProviderDeliveryReportEvent`, updates recipient rows, then
publishes `SmsFinalisedEvent` via `ApplicationEventPublisher`. The event carries:
- `clientId` — from `SendRequest.clientId`
- `sendRequestId` — human-readable id from `SendRequest.sendRequestId`
- `recipients` — list of recipient summaries
- `reservationId` — from `SendRequest.reservationId`
- `actualSegments` — sum of `segmentsConsumed` across all recipients (cast to `long`)

`SmsFinalisedBillingListener.onSmsFinalized()` receives the event and calls
`FinalBookingService.book(clientId, sendRequestId, actualSegments, reservationId)`.

`FinalBookingService.book()` contains this exact guard at line 79–83:
```java
if (actualSegmentsTotal == 0) {
    creditReservationService.release(clientId, reservationId);
    log.info("Released reservation {} for sendRequestId={} (zero actual segments)", reservationId, sendRequestId);
    return;
}
```

This is the zero-segments path — already tested, already correct.

### forceFinalize() Current Behavior (the gap)

`SmsSchedulerService.forceFinalize()` at line 121–142:
1. Loads all `SUBMITTED` recipients for the stale `SendRequest`.
2. Sets each recipient's `segmentsConsumed` to `Math.max(1, parent.getSegmentCount())`.
3. Sets each recipient's status to `FAILED`.
4. Saves each recipient.
5. Sets `parent.sendStatus = FAIL_FINALIZED`, `parent.finalizedAt = now()`.
6. Saves the parent.
7. Logs. **Done — no event published.**

The recipient rows are given a non-zero `segmentsConsumed` value (estimated), but the reservation
is never debited or released. Credits are permanently held.

### Fix Pattern for forceFinalize()

Inject `ApplicationEventPublisher` into `SmsSchedulerService`. At the end of `forceFinalize()`,
after saving the parent, publish `SmsFinalisedEvent` with `actualSegments = 0`. This triggers
`FinalBookingService.book()` which will call `release()` and return immediately.

Why `actualSegments = 0` rather than the estimated segment count?

- `forceFinalize()` is a timeout fallback; Nexah never sent a DLR. The actual segment count is
  unknown — the estimated value in `segmentsConsumed` is a placeholder.
- Billing should not charge for segments that were never confirmed as delivered.
- `FAIL_FINALIZED` status already signals that the send failed. Releasing the full reservation
  (rather than debiting an estimate) is the correct financial treatment for a failed send.
- This aligns with the zero-segments path in `FinalBookingService`: zero segments = all failed =
  release the reservation.

The estimated `segmentsConsumed` on recipient rows can stay as-is (it is useful for record-keeping
and does not affect billing when `actualSegments=0` is passed to the event).

### Transaction Boundary for the Event

`recoverStaleSms()` is `@Transactional`. `forceFinalize()` runs within that transaction (it is a
private method, no separate transaction). Publishing an `ApplicationEvent` inside a Spring transaction
causes the event to be delivered synchronously (same thread) when using the default
`ApplicationEventPublisher`. The `SmsFinalisedBillingListener` is `@Transactional` itself (with
`propagation=REQUIRED`), so it participates in the enclosing transaction from `recoverStaleSms()`.

`FinalBookingService` is class-level `@Transactional` with `propagation=REQUIRED` (per prior
decisions). All billing writes for the release happen within the same transaction. If anything
throws, the full unit of work rolls back — consistent behaviour.

No ordering constraint issues. The parent `SendRequest` must be saved before the event is published
(it is), because `FinalBookingService.book()` loads the `SendRequest` by `clientId +
sendRequestId` to read `rawExpectedCredits` and `reservedCredits`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Release credits for failed SMS | Custom SQL update or direct repo call | `CreditReservationService.release()` via `FinalBookingService.book(actualSegments=0)` |
| Cross-module event delivery | Direct service-to-service call from sms → billing | `ApplicationEventPublisher` (same pattern as `SmsProviderReportListener`) |

---

## Common Pitfalls

### Pitfall 1: Publishing Event With Estimated Segment Count

**What goes wrong:** Passing the estimated `segmentsConsumed` value (rather than 0) to
`SmsFinalisedEvent.actualSegments` would trigger the debit path in `FinalBookingService`, not the
release path. Since the estimate equals the reserved amount, `CreditReservationService.debit()` would
be called — the reservation would be consumed as a full charge rather than refunded. This is wrong
for a timed-out send.

**How to avoid:** Always use `actualSegments = 0` in `forceFinalize()`. The zero-segments guard in
`FinalBookingService.book()` is specifically designed for this case.

### Pitfall 2: Calling CreditReservationService.release() Directly from SmsSchedulerService

**What goes wrong:** `SmsSchedulerService` is in `gateway.sms.service`. Injecting
`CreditReservationService` (in `gateway.billing.service`) creates a cross-module service dependency
from sms → billing. While the architecture permits service-to-service injection, it introduces a
direct coupling that bypasses the established event-driven pattern. The event pattern already exists
for exactly this use case.

**How to avoid:** Use `ApplicationEventPublisher` to publish `SmsFinalisedEvent`. The event is
defined in `gateway.sms.contract` (same module), so no cross-module import is needed for the event
itself. Only `ApplicationEventPublisher` (Spring Framework) is added as a dependency.

### Pitfall 3: Forgetting to Update the Test for SmsSchedulerService

**What goes wrong:** `SmsSchedulerServiceTest.recoverStaleSms_success()` currently verifies that
`sendRequestRepository.save()` and `recipientRepository.save()` are called. After injecting
`ApplicationEventPublisher`, the test must also provide a mock for it or the injection will fail
under `@ExtendWith(MockitoExtension.class)`. Additionally, the test should verify that the event
is published.

**How to avoid:** Add `@Mock ApplicationEventPublisher eventPublisher` to the test class.
Verify `eventPublisher.publishEvent(any(SmsFinalisedEvent.class))` is called in the
`recoverStaleSms_success` test.

### Pitfall 4: NPE Risk With Boxed Long (the existing risk, not after fix)

**What goes wrong:** `SmsFinalisedEvent.actualSegments()` returns `Long` (boxed). The call at
`SmsFinalisedBillingListener` line 26 passes it to `FinalBookingService.book(..., long
actualSegmentsTotal, ...)` — a primitive `long` parameter. Java auto-unboxes here. If `actualSegments`
were `null`, a `NullPointerException` would be thrown at the listener boundary, inside an
`@EventListener` method, with no clear error surfacing.

Current `SmsProviderReportListener` always produces a non-null value (`(long) totalActualSegments`
where `totalActualSegments` is computed from `mapToInt(...).sum()` which is always non-null).
With `forceFinalize()` also producing a non-null value, the practical risk is low. Still, the fix
(change `Long` to `long` in the record) is one character and eliminates the theoretical hazard
entirely.

**After fix:** `SmsFinalisedEvent` record uses primitive `long actualSegments`. Callers that pass
`Long` will require explicit unboxing, making any null-producing call a compile error.

### Pitfall 5: Forgetting the SmsFinalisedBillingListenerTest

After changing `actualSegments` from `Long` to `long` in `SmsFinalisedEvent`, the existing test
at line 27 — `new SmsFinalisedEvent(100L, "req-123", List.of(), 42L, 2L)` — still compiles
correctly because `2L` is a `long` literal. No test changes required for the type fix. Verify
this at compilation time.

---

## Files That Need to Change

### Fix 1: Credit Reservation Leak in forceFinalize()

**Files to change:**
1. `src/main/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerService.java`
   - Add `ApplicationEventPublisher` field (inject via constructor, `@RequiredArgsConstructor`)
   - At end of `forceFinalize()`, after `sendRequestRepository.save(parent)`, publish:
     ```java
     eventPublisher.publishEvent(new SmsFinalisedEvent(
         parent.getClientId(),
         parent.getSendRequestId(),
         List.of(),          // no recipient summaries needed for billing path
         parent.getReservationId(),
         0L                  // zero actual segments → triggers release()
     ));
     ```

2. `src/test/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerServiceTest.java`
   - Add `@Mock ApplicationEventPublisher eventPublisher`
   - In `recoverStaleSms_success`: add `verify(eventPublisher).publishEvent(any(SmsFinalisedEvent.class))`

### Fix 2: Boxed Long Type Safety

**Files to change:**
1. `src/main/java/com/softropic/sendam/gateway/sms/contract/SmsFinalisedEvent.java`
   - Change `Long actualSegments` to `long actualSegments` in record component

**No other files need changes** — callers already pass non-null values:
- `SmsProviderReportListener` at line 123: `(long) totalActualSegments` — primitive cast, still valid
- `SmsSchedulerService.forceFinalize()` (after Fix 1): `0L` — primitive literal, valid
- `SmsFinalisedBillingListener` at line 25: `event.actualSegments()` — now returns `long`, passes to
  `book(... long actualSegmentsTotal ...)` without boxing/unboxing, eliminates NPE risk
- Existing test constructors pass `long` literals — still compile correctly

### Fix 3: Javadoc Typo

**Files to change:**
1. `src/main/java/com/softropic/sendam/gateway/billing/contract/InsufficientPlatformBalanceException.java`
   - Line 7: Change `"HTTP 400 and error_code INSUFFICIENT_PLATFORM_BALANCE"` →
     `"HTTP 422 and error_code INSUFFICIENT_PLATFORM_BALANCE"`
   - No runtime behavior changes. No test changes.

---

## Code Examples

### Verified: CreditReservationService.release() Signature
```java
// Source: direct read of CreditReservationService.java line 132
public void release(Long clientId, Long reservationId) { ... }
```
Requires `Long` (boxed) for both parameters. `SendRequest.reservationId` is `Long` (line 68 of
SendRequest.java), so `parent.getReservationId()` matches directly.

### Verified: FinalBookingService.book() Zero-Segments Guard
```java
// Source: direct read of FinalBookingService.java lines 73–83
if (reservationId == null) {
    log.warn("No reservationId found for sendRequestId={} — skipping billing finalization", sendRequestId);
    return;
}
if (actualSegmentsTotal == 0) {
    creditReservationService.release(clientId, reservationId);
    log.info("Released reservation {} for sendRequestId={} (zero actual segments)", reservationId, sendRequestId);
    return;
}
```
Both guards are already in place. Passing `actualSegments=0` with a non-null `reservationId` will
hit the second guard and release correctly.

### Verified: SmsProviderReportListener Event Publishing Pattern
```java
// Source: direct read of SmsProviderReportListener.java lines 118–124
eventPublisher.publishEvent(new SmsFinalisedEvent(
    parent.getClientId(),
    parent.getSendRequestId(),
    summaries,
    parent.getReservationId(),
    (long) totalActualSegments
));
```
`forceFinalize()` should follow the same pattern with `0L` for actual segments and `List.of()` for
recipients (the billing listener only uses `clientId`, `sendRequestId`, `reservationId`, and
`actualSegments`).

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Direct repo save in forceFinalize (incomplete) | Publish SmsFinalisedEvent with 0 segments | Reservation released, no credits held in limbo |
| `Long actualSegments` (boxed, NPE risk) | `long actualSegments` (primitive, safe) | Compile-time null safety at event boundary |
| Javadoc says HTTP 400 | Javadoc says HTTP 422 | Comment matches runtime behaviour |

---

## Open Questions

None. All three tech debt items have clear, verified fixes from codebase inspection.

---

## Sources

### Primary (HIGH confidence)
- Direct read of `SmsSchedulerService.java` — `forceFinalize()` implementation confirmed, no event published
- Direct read of `SmsFinalisedEvent.java` — `Long actualSegments` confirmed at record component level
- Direct read of `InsufficientPlatformBalanceException.java` — line 7 Javadoc says "HTTP 400" confirmed
- Direct read of `FinalBookingService.java` — zero-segments guard confirmed at lines 79–83
- Direct read of `CreditReservationService.java` — `release()` method signature confirmed
- Direct read of `SmsProviderReportListener.java` — event publishing pattern confirmed
- Direct read of `SmsFinalisedBillingListener.java` — auto-unboxing call confirmed at line 25
- Direct read of `SendRequest.java` — `reservationId` column is `Long`, nullable false at column level but typed `Long`
- Direct read of `ApiAdvice.java` — `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)` confirmed for `InsufficientPlatformBalanceException`
- Direct read of `SmsSchedulerServiceTest.java` — test structure confirmed, `@Mock` fields identified
- Direct read of `SmsFinalisedBillingListenerTest.java` — test uses `long` literals, will compile after fix

---

## Metadata

**Confidence breakdown:**
- Fix 1 (reservation leak): HIGH — root cause confirmed by reading forceFinalize(), fix path confirmed by reading FinalBookingService.book()
- Fix 2 (boxed Long): HIGH — field type confirmed in record definition, all call sites inspected
- Fix 3 (Javadoc): HIGH — both the incorrect Javadoc and the correct ApiAdvice handler directly read

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable codebase, no external dependencies)
