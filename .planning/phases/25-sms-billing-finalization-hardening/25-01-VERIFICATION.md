---
phase: 25-sms-billing-finalization-hardening
verified: 2026-03-17T19:45:00Z
status: passed
score: 3/3 must-haves verified
gaps: []
---

# Phase 25: SMS Billing Finalization Hardening — Verification Report

**Phase Goal:** Close three tech debt items: fix the stale SMS credit reservation leak in
SmsSchedulerService.forceFinalize(), harden the boxed-Long NPE risk at the billing listener
boundary (SmsFinalisedEvent.actualSegments), and fix a Javadoc typo in
InsufficientPlatformBalanceException.
**Verified:** 2026-03-17T19:45:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                         | Status     | Evidence                                                                                                               |
|----|-----------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------|
| 1  | A stale SMS that forceFinalize() processes has its credit reservation released, not permanently held | VERIFIED | SmsSchedulerService.java lines 140-146: `eventPublisher.publishEvent(new SmsFinalisedEvent(…, 0L))` after `sendRequestRepository.save(parent)` |
| 2  | SmsFinalisedEvent.actualSegments() returns a primitive long, not a boxed Long                 | VERIFIED   | SmsFinalisedEvent.java line 14: `long actualSegments` (primitive, confirmed by grep)                                   |
| 3  | InsufficientPlatformBalanceException Javadoc correctly says HTTP 422                          | VERIFIED   | InsufficientPlatformBalanceException.java line 7: "Handled by ApiAdvice with HTTP 422 and error_code INSUFFICIENT_PLATFORM_BALANCE." |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact                                                                        | Expected                                                                | Status     | Details                                                                                     |
|---------------------------------------------------------------------------------|-------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------|
| `src/main/java/…/sms/contract/SmsFinalisedEvent.java`                           | Event record with primitive long actualSegments                         | VERIFIED   | Line 14: `long actualSegments`. File is 21 lines, substantive record definition. Imported by 4 production classes. |
| `src/main/java/…/sms/service/SmsSchedulerService.java`                          | forceFinalize() publishes SmsFinalisedEvent with actualSegments=0       | VERIFIED   | Lines 140-146: `eventPublisher.publishEvent(new SmsFinalisedEvent(…, 0L))`. ApplicationEventPublisher injected via `@RequiredArgsConstructor` at line 34. |
| `src/test/java/…/sms/service/SmsSchedulerServiceTest.java`                      | Test verifies event published in recoverStaleSms_success                | VERIFIED   | Line 96: `verify(eventPublisher).publishEvent(any(SmsFinalisedEvent.class))`. @Mock ApplicationEventPublisher at line 39. |
| `src/main/java/…/billing/contract/InsufficientPlatformBalanceException.java`    | Correct Javadoc with HTTP 422                                           | VERIFIED   | Line 7 reads "HTTP 422". File is 33 lines, full class implementation.                       |

### Key Link Verification

| From                                    | To                         | Via                                                                    | Status   | Details                                                                                                   |
|-----------------------------------------|----------------------------|------------------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------|
| SmsSchedulerService.forceFinalize()     | FinalBookingService.book() | `eventPublisher.publishEvent(new SmsFinalisedEvent(…, 0L))`            | WIRED    | Lines 140-146 in SmsSchedulerService.java. Event is published after `sendRequestRepository.save(parent)` with `0L` actualSegments, routing through existing zero-segments guard in FinalBookingService. |
| SmsProviderReportListener               | SmsFinalisedEvent          | `(long) totalActualSegments` at construction                           | WIRED    | Cast from `int` to primitive `long` — compatible with changed field type. No regression introduced.       |
| SmsFinalisedBillingListenerTest callers | SmsFinalisedEvent          | Long literals `2L`, `0L`, `5L` in test constructor calls               | WIRED    | All three test usages pass `long`-compatible literals. Primitive field accepts these directly.             |

### Requirements Coverage

| Requirement                                  | Status    | Notes                                                             |
|----------------------------------------------|-----------|-------------------------------------------------------------------|
| Credit reservation leak closed               | SATISFIED | forceFinalize() publishes SmsFinalisedEvent(0L) which releases reservation via existing FinalBookingService.book() zero-segments guard |
| SmsFinalisedEvent NPE hazard eliminated      | SATISFIED | actualSegments is primitive `long`; no unboxing NullPointerException possible at listener boundary |
| Javadoc accuracy for InsufficientPlatformBalanceException | SATISFIED | Line 7 corrected from "HTTP 400" to "HTTP 422" matching runtime ApiAdvice @ResponseStatus(UNPROCESSABLE_ENTITY) |

### Anti-Patterns Found

No blockers or warnings detected.

| File                          | Line  | Pattern      | Severity | Impact  |
|-------------------------------|-------|--------------|----------|---------|
| SmsSchedulerService.java      | 97-100 | Stale Javadoc comment block references old "Refactoring Note" | INFO | Cosmetic; does not affect correctness. The TODO comment block mentioned in the plan (lines 133-136) was removed as intended. |

### Human Verification Required

None. All three changes are structural (type change, new method call, comment correction). No visual
or real-time behavior to validate. Programmatic verification is sufficient.

### Gaps Summary

No gaps. All three tech debt items are fully implemented and verified in the actual codebase:

1. **Credit reservation leak** — `SmsSchedulerService.forceFinalize()` publishes
   `SmsFinalisedEvent` with `actualSegments=0L` at lines 140-146, after saving the parent
   `SendRequest`. The zero-segments path in `FinalBookingService.book()` calls
   `CreditReservationService.release()` to free the held reservation.

2. **Boxed Long NPE hazard** — `SmsFinalisedEvent.actualSegments` is `long` (primitive) at line 14.
   All three callers (`SmsSchedulerService`: `0L`, `SmsProviderReportListener`: `(long)
   totalActualSegments`, `SmsFinalisedBillingListenerTest`: `2L`/`0L`/`5L`) use primitive-compatible
   values. No regression risk.

3. **Javadoc typo** — `InsufficientPlatformBalanceException` line 7 reads "HTTP 422" matching the
   `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)` handler in `ApiAdvice`. Commits `894dc2e`
   (fix) and `7d3a95c` (docs) are present in git history confirming the changes landed on the
   branch.

---

_Verified: 2026-03-17T19:45:00Z_
_Verifier: Claude (gsd-verifier)_
