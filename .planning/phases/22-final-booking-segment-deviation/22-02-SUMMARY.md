---
phase: 22-final-booking-segment-deviation
plan: 02
subsystem: billing
tags: [spring, jpa, transactional, pessimistic-locking, event-publishing, jackson, jsonb, booking]

# Dependency graph
requires:
  - phase: 22-01
    provides: SegmentDeviationAlert entity, SegmentDeviationAlertRepository, DeviationAlertType enum, SMS_EXTRA_DEBIT, SEGMENT_DEVIATION, PLATFORM_FREEZE_SHORTFALL
  - phase: 21-enhanced-credit-reservation
    provides: rawExpectedCredits and reservedCredits fields on SendRequest used for delta calculation
  - phase: 20-account-freeze
    provides: ClientFreezeService.freeze() and PlatformFreezeService.freeze() called in BOOK-05/06
  - phase: 19-platform-ledger
    provides: PlatformCreditService.applyLedgerEntry() and PlatformLedgerEntryType.SHORTFALL_ABSORPTION
provides:
  - FinalBookingService with full BOOK-01 through BOOK-06 decision tree
  - SegmentDeviationService persisting SegmentDeviationAlert records with JSONB per-recipient breakdown
  - SmsFinalisedBillingListener reduced to a thin shim delegating to FinalBookingService.book()
  - SEGDEV-01 through SEGDEV-04 and BOOK-01 through BOOK-06 requirements satisfied
affects:
  - 22-03-PLAN.md: No further service changes needed; Plan 03 covers tests for FinalBookingService
  - phase-24: FinalBookingService creates all SegmentDeviationAlert records that Phase 24 admin API will list/filter

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "6-scenario booking decision tree: BOOK-01/02/03 (actual <= reserved), BOOK-04/05/06 (actual > reserved)"
    - "Three-lock order: (1) reservation row via debit(), (2) client balance via findByClientIdForUpdate(), (3) platform balance via findForUpdate()"
    - "Thin listener shim pattern: @EventListener method delegates entirely to service; no business logic in listener"
    - "Double alert pattern for BOOK-06: SEGMENT alert (client deviation) + PLATFORM_FREEZE alert (platform incident)"
    - "buildBreakdown() helper: loads recipients by FK then maps to RecipientDeviationEntry records for JSONB"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/service/SegmentDeviationService.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/FinalBookingService.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/billing/service/SmsFinalisedBillingListener.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/SmsFinalisedBillingListenerTest.java

key-decisions:
  - "BOOK-06 issues two alerts (SEGMENT + PLATFORM_FREEZE) in one transaction: SEGMENT records the client's deviation; PLATFORM_FREEZE records the platform-level incident for operator investigation"
  - "Client balance lock acquired before creditReservationService.debit() in BOOK-04/05/06 path: ensures clientAvailable read is atomic with subsequent debit; re-entrant lock within same transaction is safe on PostgreSQL"
  - "platformAvailable > 0 guard before BOOK-06 partial absorption: prevents applyLedgerEntry(-0) call; mirrors clientAvailable > 0 guard for drain step"
  - "SegmentDeviationAlertData record defined inside SegmentDeviationService: data transfer object co-located with the service that owns it; FinalBookingService imports via static inner reference"

patterns-established:
  - "Booking scenario pattern: compute delta from rawExpectedCredits vs actual; branch on actual vs reservedTotal for buffer coverage"
  - "Lock order comment in Javadoc: documenting (1)/(2)/(3) lock acquisition points prevents future inversion"

# Metrics
duration: 35min
completed: 2026-03-17
---

# Phase 22 Plan 02: Final Booking Service Summary

**FinalBookingService with 6-scenario BOOK-01/06 decision tree + SegmentDeviationService JSONB alert persistence — SmsFinalisedBillingListener reduced to a thin delegation shim; 195 tests pass**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-17T14:36:20Z
- **Completed:** 2026-03-17T15:11:00Z
- **Tasks:** 2/2
- **Files modified:** 4 (2 created, 2 updated)

## Accomplishments

- SegmentDeviationService.createAlert() persists SegmentDeviationAlert with JSONB per-recipient breakdown and publishes SEGMENT_DEVIATION or PLATFORM_FREEZE_SHORTFALL audit event
- FinalBookingService.book() implements all 6 BOOK scenarios with correct pessimistic lock ordering: reservation row → client balance → platform balance
- BOOK-01/02/03: debit(reservationId, actual); BOOK-02 exact match skips alert; BOOK-01/03 create REFUNDED alert
- BOOK-04: extra client debit via SMS_EXTRA_DEBIT ledger entry; EXTRA_DEBITED alert
- BOOK-05: drain client to zero, platform absorbs full shortfall via SHORTFALL_ABSORPTION; client frozen; SHORTFALL_ABSORBED alert
- BOOK-06: partial platform absorption, platform frozen; two alerts (SEGMENT + PLATFORM_FREEZE); unrecoveredAmount recorded
- SmsFinalisedBillingListener reduced from ~20 lines of business logic to a 5-line delegation shim
- SmsFinalisedBillingListenerTest updated with 3 tests verifying FinalBookingService.book() delegation

## Task Commits

Each task was committed atomically:

1. **Task 1: SegmentDeviationService — alert persistence** - `7a1a37a` (feat)
2. **Task 2: FinalBookingService 6-scenario orchestration + listener shim update** - `e3343fe` (feat)

**Plan metadata:** (committed after SUMMARY.md and STATE.md update)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/billing/service/SegmentDeviationService.java` - New service; SegmentDeviationAlertData record; createAlert() persists alert and publishes audit event
- `src/main/java/com/softropic/sendam/gateway/billing/service/FinalBookingService.java` - New service; full BOOK-01 through BOOK-06 decision tree; buildBreakdown() helper for JSONB recipient data
- `src/main/java/com/softropic/sendam/gateway/billing/service/SmsFinalisedBillingListener.java` - Replaced direct credit operations with single finalBookingService.book() delegation call
- `src/test/java/com/softropic/sendam/gateway/billing/service/SmsFinalisedBillingListenerTest.java` - Replaced CreditReservationService mock with FinalBookingService mock; 3 tests verify correct argument passing

## Decisions Made

- **BOOK-06 double alert:** Two createAlert() calls in a single transaction: first `DeviationAlertType.SEGMENT` records the client's segment deviation (for per-client audit), second `DeviationAlertType.PLATFORM_FREEZE` records the platform-level incident (for operator investigation). The plan explicitly required both.
- **Client balance lock order in BOOK-04/05/06:** `findByClientIdForUpdate()` called before `creditReservationService.debit()` to read `clientAvailable` atomically before any balance mutation. PostgreSQL re-entrant locking within the same transaction means the subsequent `debit()` call (which also calls `findByClientIdForUpdate()`) acquires the same lock with no contention.
- **Zero-guard for partial absorption in BOOK-06:** Added `if (platformAvailable > 0)` before `platformCreditService.applyLedgerEntry(-platformAvailable, ...)` to prevent a zero-amount ledger entry when platform balance is already zero. Same guard applied for client drain in BOOK-05/06.

## Deviations from Plan

None — plan executed exactly as written. All 6 scenarios, both services, and the listener shim match the plan specification.

## Issues Encountered

None — both services compiled on first attempt. All 195 tests passed immediately after updating the listener test.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 03 (FinalBookingService unit tests) can now reference:
- `FinalBookingService.book()` with injected mocks for all 10 dependencies
- `SegmentDeviationService.createAlert()` with `SegmentDeviationAlertData` record
- All 6 BOOK scenarios are implemented and testable with Mockito

No blockers. 195 tests pass.

---
*Phase: 22-final-booking-segment-deviation*
*Completed: 2026-03-17*
