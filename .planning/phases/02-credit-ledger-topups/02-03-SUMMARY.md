---
phase: 02-credit-ledger-topups
plan: "03"
subsystem: payments
tags: [spring-data, jpa, pessimistic-locking, credits, reservation, transactional, mockito]

# Dependency graph
requires:
  - phase: 02-credit-ledger-topups
    plan: "01"
    provides: ClientCreditBalanceRepository.findByClientIdForUpdate (PESSIMISTIC_WRITE), CreditLedgerEntry, CreditLedgerRepository, LedgerEntryType enum, InsufficientBalanceException
provides:
  - CreditReservationService: reserve(clientId, amount, reference) → long reservationId
  - CreditReservationService: release(clientId, reservationId) → void
  - CreditReservationService: debit(clientId, reservationId, actualAmount) → void
  - Unit test suite: CreditReservationServiceTest — 7 tests covering all operation variants including concurrent reservation invariant
affects:
  - 03-sms-send (SMS send endpoint calls reserve/debit/release on CreditReservationService)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Atomic credit reservation: reserve() → debit()/release() lifecycle for SMS billing
    - Two-phase credit commit: reserve deducts synchronously, debit settles with actual provider count
    - Over-reservation refund: SMS_REFUND entry written automatically when actualAmount < reservedAmount
    - Independent lock acquisition: CreditReservationService acquires findByClientIdForUpdate independently in every method, not relying on a prior lock still being held

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/service/CreditReservationService.java
    - src/test/java/com/softropic/sendam/client/service/CreditReservationServiceTest.java
  modified: []

key-decisions:
  - "CreditReservationService manages its own lock acquisition independently — not delegating to CreditService.applyLedgerEntry() avoids implicit dependency on call ordering within a single transaction"
  - "reserve() returns the ledger entry id as reservationId — Phase 3 passes this back to debit()/release() to load the original reservation and derive the reserved amount"
  - "debit() does NOT subtract actualAmount from balance again — balance was already reduced by reserve(); debit only adjusts the balance upward for over-reservation"
  - "release() and debit() both re-acquire findByClientIdForUpdate — not relying on a prior lock still being held; each method is independently correct"

patterns-established:
  - "reserve → debit/release lifecycle: Phase 3 must call reserve() at send time, debit() on provider confirmation, release() on failure"
  - "Over-reservation refund: when provider confirms fewer segments than estimated, debit() automatically writes SMS_REFUND for the difference"

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 2 Plan 03: Credit Reservation Service Summary

**Atomic reserve/debit/release service using SELECT FOR UPDATE pessimistic locking — the financial enforcement layer that guarantees balance never goes negative under concurrent SMS sends**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-10T15:38:00Z
- **Completed:** 2026-03-10T15:46:55Z
- **Tasks:** 2
- **Files modified:** 2 (2 created, 0 modified)

## Accomplishments

- `CreditReservationService.reserve()` acquires SELECT FOR UPDATE, checks balance, writes SMS_RESERVATION (amount=-N), reduces balance, returns reservationId — the Phase 3 SMS send endpoint's single entry point for credit enforcement
- `debit()` writes SMS_DEBIT for actualAmount and, when actualAmount < reservedAmount, additionally writes SMS_REFUND for the difference — handles provider-confirmed segment counts that differ from estimates
- 7-test pure-Mockito suite including `concurrentReserve_onlyOneSucceeds` which proves via AtomicLong simulation that two sequential reserve calls where the sum exceeds the balance yield exactly one success and one InsufficientBalanceException with the final balance never negative

## Task Commits

Each task was committed atomically:

1. **Task 1: CreditReservationService — reserve, release, debit** - `2f21981` (feat)
2. **Task 2: Unit tests for concurrent reservation invariant** - `30f1196` (test)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/client/service/CreditReservationService.java` - reserve/release/debit with SELECT FOR UPDATE on every balance mutation
- `src/test/java/com/softropic/sendam/client/service/CreditReservationServiceTest.java` - 7 Mockito tests including concurrent invariant

## Decisions Made

- **Independent lock acquisition in every method:** `release()` and `debit()` re-acquire `findByClientIdForUpdate` rather than relying on a prior lock. This makes each method independently correct regardless of call context, and avoids fragile assumptions about transaction boundary sharing.
- **debit() does not subtract from balance again:** The balance was already reduced by `reserve()`. `debit()` only adjusts upward for over-reservation. This is documented prominently in the implementation to prevent future regression.
- **No delegation to CreditService.applyLedgerEntry():** CreditReservationService manages its own lock acquisition to keep the reservation lifecycle self-contained and avoid creating an implicit dependency on call ordering.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `ResourceNotFoundException` in this codebase requires two arguments (message + resourceName) rather than the one-argument form shown in the plan pseudocode. Applied the correct two-argument constructor consistently — this was a pseudocode simplification in the plan, not a deviation.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `CreditReservationService` is a `@Service` bean — Spring will autowire it to the Phase 3 SMS send endpoint with no additional wiring
- Method signatures ready for Phase 3:
  - `reserve(Long clientId, long amount, String reference) → long` (reservationId)
  - `release(Long clientId, Long reservationId) → void`
  - `debit(Long clientId, Long reservationId, long actualAmount) → void`
- LockTimeoutException handling: add `@ExceptionHandler` in `ApiAdvice` for `javax.persistence.LockTimeoutException` → HTTP 503 when Phase 3 SMS endpoint exists (noted in plan)
- No blockers for Phase 3

---
*Phase: 02-credit-ledger-topups*
*Completed: 2026-03-10*
