---
phase: 20-account-freeze-infrastructure
plan: 01
subsystem: database
tags: [flyway, jpa, hibernate, postgres, pessimistic-locking, enums]

# Dependency graph
requires:
  - phase: 19-platform-credit-account
    provides: PlatformCreditBalance singleton pattern (id=1 seed, findForUpdate() pessimistic lock), AbstractAuditingEntity base entity
provides:
  - V12 Flyway migration with 5 freeze columns on client_account and platform_freeze_state singleton table
  - ClientEntity freeze fields (frozen, frozenAt, freezeReason, freezeResolvedAt, freezeResolution)
  - ClientRepository.findByIdForUpdate() pessimistic-lock query
  - PlatformFreezeState JPA entity (billing.repo)
  - PlatformFreezeStateRepository with findState() and findForUpdate()
  - SendRequestStatus.SUSPENDED enum constant
  - SendRequestRepository bulk queries: suspendScheduledForClient, resumeScheduledForClient, suspendAllScheduled, resumeAllScheduled
affects:
  - 20-02 (freeze service business logic uses all these foundations)
  - 20-03 (freeze REST layer)
  - 22 (PFLAT-03 sets shortfall_amount on platform freeze trigger)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Singleton entity pattern: id=1 seeded row, application always updates via SELECT FOR UPDATE (same as PlatformCreditBalance)
    - Pessimistic lock timeout: 2000ms on all findForUpdate() queries, consistent with existing billing repo pattern
    - Fully-qualified JPQL enum references: com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED avoids import ambiguity in @Query strings

key-files:
  created:
    - src/main/resources/db/migration/V12__account_freeze.sql
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformFreezeState.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformFreezeStateRepository.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/account/repo/ClientEntity.java
    - src/main/java/com/softropic/sendam/gateway/account/repo/ClientRepository.java
    - src/main/java/com/softropic/sendam/gateway/sms/contract/SendRequestStatus.java
    - src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRepository.java

key-decisions:
  - "PlatformFreezeState.shortfall_amount is nullable — manually-initiated admin freezes have no shortfall; only Phase 22 PFLAT-03 (auto low-balance trigger) sets this field"
  - "SUSPENDED inserted between ACCEPTED and SUBMITTED in SendRequestStatus — new constant is backward-compatible; no existing code switches on exhaustive enum; resumes to ACCEPTED on unfreeze"
  - "ClientRepository.findByIdForUpdate() mirrors ClientCreditBalanceRepository pattern exactly: @Lock(PESSIMISTIC_WRITE), @Query with id param, @QueryHints 2000ms timeout"
  - "PlatformFreezeStateRepository has both findState() (non-locking read for checking state) and findForUpdate() (pessimistic lock for state transitions) — two-method pattern mirrors PlatformCreditBalanceRepository"

patterns-established:
  - "Freeze field naming: frozen (boolean), frozen_at (Instant/TIMESTAMP), freeze_reason (TEXT), freeze_resolved_at (Instant/TIMESTAMP), freeze_resolution (TEXT) — same on both client_account and platform_freeze_state"
  - "Bulk @Modifying queries in SendRequestRepository use fully-qualified enum class name in JPQL to avoid import ambiguity"

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 20 Plan 01: Account Freeze Infrastructure — Schema and Data Layer Summary

**Flyway V12 migration with freeze columns on client_account and platform_freeze_state singleton, plus JPA entities, pessimistic-lock repositories, SUSPENDED enum, and four bulk query methods on SendRequestRepository**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-17T11:58:44Z
- **Completed:** 2026-03-17T12:04:01Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- V12 Flyway migration adds 5 freeze columns to client_account and creates platform_freeze_state singleton table with seeded id=1 row
- ClientEntity gains 5 freeze fields (boolean + Instant + String types) with explicit getters/setters; PlatformFreezeState JPA entity and PlatformFreezeStateRepository created following PlatformCreditBalance singleton pattern
- SendRequestStatus.SUSPENDED added for scheduled-send suspension; four bulk @Modifying queries on SendRequestRepository enable atomic freeze/unfreeze of scheduled sends per-client and platform-wide

## Task Commits

Each task was committed atomically:

1. **Task 1: V12 Flyway migration** - `24c781f` (chore)
2. **Task 2: Entity + repository additions** - `364919d` (feat)

**Plan metadata:** (see final commit below)

## Files Created/Modified

- `src/main/resources/db/migration/V12__account_freeze.sql` — ALTER client_account + CREATE platform_freeze_state + seed row
- `src/main/java/com/softropic/sendam/gateway/account/repo/ClientEntity.java` — 5 freeze fields with getters/setters
- `src/main/java/com/softropic/sendam/gateway/account/repo/ClientRepository.java` — findByIdForUpdate() with PESSIMISTIC_WRITE
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformFreezeState.java` — new singleton JPA entity
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformFreezeStateRepository.java` — findState() + findForUpdate()
- `src/main/java/com/softropic/sendam/gateway/sms/contract/SendRequestStatus.java` — added SUSPENDED between ACCEPTED and SUBMITTED
- `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRepository.java` — 4 bulk @Modifying queries

## Decisions Made

- **shortfall_amount nullable:** PlatformFreezeState.shortfall_amount is nullable. Manually-initiated admin freezes have no shortfall amount — only Phase 22 PFLAT-03 (automatic low-balance trigger) populates this field.
- **SUSPENDED placement:** Inserted between ACCEPTED and SUBMITTED. This placement reflects the lifecycle: a scheduled send is ACCEPTED (queued), then optionally SUSPENDED (frozen), then when unfrozen returns to ACCEPTED before being SUBMITTED to the provider.
- **Two-method repository pattern on PlatformFreezeStateRepository:** `findState()` (non-locking, safe for read-only checks) and `findForUpdate()` (pessimistic write lock, used only during state transitions). Mirrors PlatformCreditBalanceRepository's `findBalance()` / `findForUpdate()` pattern.
- **Fully-qualified enum in JPQL:** All four bulk queries in SendRequestRepository use `com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED` (fully qualified) to avoid import ambiguity in JPQL strings — consistent with existing queries in the same file.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 02 (FreezeService business logic) can proceed immediately. All foundations are in place:
- Schema migration applied cleanly (177 tests pass, BUILD SUCCESS)
- ClientEntity freeze fields mapped to correct column names
- PlatformFreezeState entity and repository ready for service injection
- SendRequestStatus.SUSPENDED available for status transitions
- All four bulk repository methods ready for service calls inside @Transactional contexts

---
*Phase: 20-account-freeze-infrastructure*
*Completed: 2026-03-17*
