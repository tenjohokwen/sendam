---
phase: 23-periodic-balance-reconciliation
plan: 02
subsystem: billing
tags: [spring-scheduler, mockito, junit5, nexah, reconciliation, audit-events]

# Dependency graph
requires:
  - phase: 23-01
    provides: BalanceDeviationAlert entity + repo, AuditEventType.BALANCE_DEVIATION, DeviationAlertType.BALANCE, NexahClient.fetchCreditBalance(), ReconciliationProperties
  - phase: 19-platform-credit-tracking
    provides: PlatformCreditService.getBalance(), PlatformBalanceResponse
  - phase: 22-final-booking-segment-deviation
    provides: SegmentDeviationService split pattern (HTTP call outside transaction, @Transactional write in separate service)

provides:
  - BalanceDeviationAlertService — @Transactional write service saving BalanceDeviationAlert and publishing BALANCE_DEVIATION audit event
  - BalanceReconciliationJob — @Scheduled job polling Nexah /smscredit, comparing against platform balance, creating alert on mismatch
  - BalanceReconciliationJobTest — 3 unit tests covering exact-match, mismatch, and Nexah-unavailable paths
  - Phase 23 complete — periodic balance reconciliation fully implemented (BALREC-01 through BALREC-04)

affects:
  - 24 (Phase 24 admin UI surfaces BalanceDeviationAlert records; job must be running for alerts to accumulate)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "HTTP-outside-transaction split: BalanceReconciliationJob (no @Transactional) calls NexahClient then delegates to BalanceDeviationAlertService (@Transactional) — DB connection never held during Nexah HTTP call"
    - "Silent skip pattern: catch Exception in reconcile() → log.warn + return; platform balance not read on Nexah failure (short-circuit)"
    - "SpEL fixedDelayString: #{${sendam.reconciliation.interval-minutes:15} * 60000L} — converts minutes to ms at context startup; default 15"
    - "Platform event with null clientId: DomainAuditEvent(BALANCE_DEVIATION, null, 'system', detail) — null clientId for events with no client target"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/service/BalanceDeviationAlertService.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/BalanceReconciliationJob.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/BalanceReconciliationJobTest.java
  modified: []

key-decisions:
  - "BalanceReconciliationJob has no class-level @Transactional — the Nexah HTTP call must not hold a DB connection; mirrors SegmentDeviationService split pattern from Phase 22"
  - "Silent skip on Nexah exception: catch any Exception (not just ProviderUnavailableException) since RestClientException is also possible; log.warn + return, no re-throw, no alert"
  - "platformCreditService.getBalance() short-circuits on Nexah failure — no point reading Sendam balance if Nexah balance is unavailable"

patterns-established:
  - "HTTP-outside-transaction split: scheduled jobs that call external HTTP AND write to DB should use two classes — job (no @Transactional) + service (@Transactional) — to prevent holding DB connection during HTTP call"

# Metrics
duration: 9min
completed: 2026-03-17
---

# Phase 23 Plan 02: Periodic Balance Reconciliation Summary

**@Scheduled BalanceReconciliationJob + @Transactional BalanceDeviationAlertService — Nexah/Sendam balance polling with mismatch alert creation; 3 unit tests; Phase 23 complete**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-17T15:50:02Z
- **Completed:** 2026-03-17T15:59:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- BalanceDeviationAlertService persists BalanceDeviationAlert (alertType=BALANCE, nexahBalance, sendamBalance, delta) and publishes BALANCE_DEVIATION audit event with null clientId — platform-level event
- BalanceReconciliationJob runs at configurable interval via SpEL fixedDelayString; silently skips on Nexah exception; calls alertService only when delta != 0
- No DB connection held during Nexah HTTP call — job is non-transactional; service opens its own transaction only when needed
- BalanceReconciliationJobTest covers all 3 BALREC paths: exact-match (no alert), mismatch (createAlert called with correct args), Nexah unavailable (silent skip, platformCreditService never called)
- 206 tests pass (203 pre-existing + 3 new)

## Task Commits

Each task was committed atomically:

1. **Task 1: BalanceDeviationAlertService + BalanceReconciliationJob** - `f5d8e70` (feat)
2. **Task 2: BalanceReconciliationJobTest unit tests** - `fe77f5e` (test)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/billing/service/BalanceDeviationAlertService.java` - @Transactional write service; save alert + publish BALANCE_DEVIATION audit event (null clientId)
- `src/main/java/com/softropic/sendam/gateway/billing/service/BalanceReconciliationJob.java` - @Scheduled job; SpEL fixedDelayString; silent skip on Nexah exception
- `src/test/java/com/softropic/sendam/gateway/billing/service/BalanceReconciliationJobTest.java` - 3 unit tests: REC-01 exact-match, REC-02 mismatch, REC-03 Nexah unavailable

## Decisions Made

- **No class-level @Transactional on BalanceReconciliationJob** — the Nexah HTTP call must not hold a DB connection. The write is delegated to BalanceDeviationAlertService which opens its own transaction only when delta != 0. Mirrors the SegmentDeviationService split pattern from Phase 22.
- **Catch any Exception on Nexah call (not just ProviderUnavailableException)** — RestClientException is also possible if the HTTP layer throws before NexahClient processes the response; broad catch is safer for a background probe.
- **Short-circuit on Nexah failure** — if Nexah balance is unavailable, there is no point reading the Sendam balance. Verified in REC-03 test: `verify(platformCreditService, never()).getBalance()`.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Phase 23 is complete. Phase 24 (Deviation Alert Management) can begin:
- BalanceDeviationAlert records are accumulating once BalanceReconciliationJob runs in a live environment
- BalanceDeviationAlertRepository is a minimal stub — Phase 24 adds findAll/filter query methods for the admin listing API
- SegmentDeviationAlert records are similarly available for Phase 24's admin listing
- No blockers

---
*Phase: 23-periodic-balance-reconciliation*
*Completed: 2026-03-17*
