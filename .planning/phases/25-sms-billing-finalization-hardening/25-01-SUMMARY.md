---
phase: 25-sms-billing-finalization-hardening
plan: 01
subsystem: billing
tags: [spring-events, sms-finalization, credit-reservation, tech-debt]

# Dependency graph
requires:
  - phase: 22-segment-deviation-detection
    provides: FinalBookingService.book() with zero-segments guard that releases reservation
  - phase: 19-platform-credit-ledger
    provides: InsufficientPlatformBalanceException with HTTP 422 runtime behaviour
provides:
  - Stale SMS credit reservation leak closed (forceFinalize publishes SmsFinalisedEvent(0L))
  - SmsFinalisedEvent.actualSegments is primitive long (no NPE hazard at listener boundary)
  - InsufficientPlatformBalanceException Javadoc accurate (HTTP 422, not 400)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Zero-actualSegments event pattern: stale/timed-out SMS publishes SmsFinalisedEvent with 0L to trigger reservation release through existing FinalBookingService.book() guard"

key-files:
  created: []
  modified:
    - src/main/java/com/softropic/sendam/gateway/sms/contract/SmsFinalisedEvent.java
    - src/main/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerService.java
    - src/test/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerServiceTest.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/InsufficientPlatformBalanceException.java

key-decisions:
  - "25-01-A: SmsFinalisedEvent.actualSegments is now primitive long. All three existing call sites used long-compatible literals (2L, 0L, 5L) so no callers required change."
  - "25-01-B: forceFinalize() always passes 0L as actualSegments — never the estimated value. Zero-segments path in FinalBookingService releases reservation and returns; correct treatment for a timed-out send where no DLR was received."
  - "25-01-C: stale SendRequest builder default reservationId=null is intentional in the test — FinalBookingService.book() has a reservationId==null guard that returns immediately; test only verifies the event is published."

patterns-established: []

# Metrics
duration: 15min
completed: 2026-03-17
---

# Phase 25 Plan 01: SMS Billing Finalization Hardening Summary

**Credit reservation leak closed: stale SMS forceFinalize() now publishes SmsFinalisedEvent(0L) through FinalBookingService to release held reservations; SmsFinalisedEvent.actualSegments made primitive long**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-17T18:16:00Z
- **Completed:** 2026-03-17T18:31:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Stale SMS credit reservation leak eliminated: SmsSchedulerService.forceFinalize() now publishes SmsFinalisedEvent with actualSegments=0L after saving the parent, routing through the existing FinalBookingService.book() zero-segments guard which calls CreditReservationService.release() to free the held reservation
- NPE hazard removed: SmsFinalisedEvent.actualSegments changed from boxed Long to primitive long, preventing theoretical NullPointerException if the event listener auto-unboxes a null
- Javadoc typo corrected: InsufficientPlatformBalanceException line 7 now reads "HTTP 422" matching the actual ApiAdvice @ResponseStatus(UNPROCESSABLE_ENTITY) handler

## Task Commits

1. **Task 1: Fix credit reservation leak and boxed Long type hazard** - `894dc2e` (fix)
2. **Task 2: Fix InsufficientPlatformBalanceException Javadoc typo** - `7d3a95c` (docs)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/sms/contract/SmsFinalisedEvent.java` - Changed `Long actualSegments` to `long actualSegments` (primitive, eliminates NPE hazard)
- `src/main/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerService.java` - Added ApplicationEventPublisher injection; forceFinalize() publishes SmsFinalisedEvent(0L) after save; removed stale TODO comment block
- `src/test/java/com/softropic/sendam/gateway/sms/service/SmsSchedulerServiceTest.java` - Added @Mock ApplicationEventPublisher; recoverStaleSms_success() verifies publishEvent called with any(SmsFinalisedEvent.class)
- `src/main/java/com/softropic/sendam/gateway/billing/contract/InsufficientPlatformBalanceException.java` - Corrected Javadoc: "HTTP 400" → "HTTP 422"

## Decisions Made

- **25-01-A: Primitive long in SmsFinalisedEvent.** All three existing call sites (SmsFinalisedBillingListenerTest: 2L, 0L, 5L; SmsProviderReportListener: `(long) totalActualSegments`) use primitive-compatible long values — no callers required change.
- **25-01-B: Always publish 0L, never the estimated value.** The stale path should not apply estimated segments to billing. Zero-segments triggers CreditReservationService.release() in FinalBookingService — exact correct treatment for a timeout with no DLR.
- **25-01-C: Test only verifies event publication, not billing chain.** The stale SendRequest has reservationId=null (builder default). FinalBookingService has a null-reservationId early return; billing chain has its own unit tests. SmsSchedulerServiceTest scope is the scheduler only.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Maven wrapper (.mvn/) absent from project; used system Maven (3.9.9) which was already in use by prior phases.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Three tech debt items from the v1.3 milestone audit are now closed
- 213 tests pass, no regressions
- Phase 25 Plan 02 (if any) can proceed immediately

---
*Phase: 25-sms-billing-finalization-hardening*
*Completed: 2026-03-17*
