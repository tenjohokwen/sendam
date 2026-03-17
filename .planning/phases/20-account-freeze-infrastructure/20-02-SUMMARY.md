---
phase: 20-account-freeze-infrastructure
plan: 02
subsystem: domain-services
tags: [freeze, audit, spring-events, pessimistic-locking, mockito, junit5]

# Dependency graph
requires:
  - phase: 20-01
    provides: ClientEntity freeze fields, ClientRepository.findByIdForUpdate(), PlatformFreezeState entity+repo, SendRequestRepository bulk queries (suspendScheduledForClient, resumeScheduledForClient, suspendAllScheduled, resumeAllScheduled)
provides:
  - AccountFrozenException (gateway.account.contract) — ApplicationException subtype with ClientError.ACCOUNT_FROZEN, carries clientId
  - PlatformFrozenException (gateway.billing.contract) — ApplicationException subtype with PlatformError.PLATFORM_FROZEN, no clientId
  - ClientError.ACCOUNT_FROZEN enum constant
  - PlatformError.PLATFORM_FROZEN enum constant
  - AuditEventType constants: CLIENT_ACCOUNT_FROZEN, CLIENT_ACCOUNT_UNFROZEN, PLATFORM_FROZEN, PLATFORM_UNFROZEN
  - ClientFreezeService with freeze(), unfreeze(), isFrozen()
  - PlatformFreezeService with freeze(), unfreeze(), isFrozen()
  - 10 new unit tests (5 per service)
affects:
  - 20-03 (REST controllers call ClientFreezeService and PlatformFreezeService; ApiAdvice maps AccountFrozenException and PlatformFrozenException)
  - 21 (CreditReservationService calls isFrozen() on both services before accepting reservations)
  - 22 (PFLAT-03 automatic low-balance trigger calls PlatformFreezeService.freeze() with shortfallAmount)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Freeze service pattern: pessimistic lock (findByIdForUpdate/findForUpdate) → mutate state → bulk SMS update → publish DomainAuditEvent — all in one @Transactional
    - resolveActor() helper: SecurityContextHolder.getContext().getAuthentication() with null-safe fallback to "system" — consistent pattern in both freeze services
    - isFrozen() readOnly: @Transactional(readOnly=true) override on isFrozen() method — class-level @Transactional is readWrite, method-level narrows to read

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/account/contract/AccountFrozenException.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformFrozenException.java
    - src/main/java/com/softropic/sendam/gateway/account/service/ClientFreezeService.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/PlatformFreezeService.java
    - src/test/java/com/softropic/sendam/gateway/account/service/ClientFreezeServiceTest.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/PlatformFreezeServiceTest.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/account/contract/ClientError.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformError.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java

key-decisions:
  - "Cross-module repo dependency: ClientFreezeService (account.service) injects SendRequestRepository (sms.repo) directly — freeze service is a domain orchestrator needing atomic SMS bulk-update; documented in class comment"
  - "PlatformFreezeService lives in billing.service — keeps CreditReservationService (also billing) calling isFrozen() within the same package without cross-module service dependency"
  - "freeze_throwsResourceNotFound test uses hasMessageContaining() — ApplicationException.getMessage() wraps with SUPPORT_ID prefix, so direct equals would be fragile"

patterns-established:
  - "Freeze lifecycle pattern: lock row (findByIdForUpdate/findForUpdate) → set frozen=true/false + timestamp + reason/resolution + reset complementary fields → save → bulk SMS update → log with count → publishEvent"
  - "Audit event detail format: key=value pairs concatenated as string, including SMS row counts for operational observability"

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 20 Plan 02: Account Freeze Infrastructure — Service Layer Summary

**ClientFreezeService and PlatformFreezeService with pessimistic-lock state transitions, atomic bulk SMS suspension/resume, DomainAuditEvent publishing, and 10 unit tests covering all lifecycle paths**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-17T12:06:23Z
- **Completed:** 2026-03-17T12:11:06Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Exception types and error codes: AccountFrozenException (with clientId), PlatformFrozenException, ClientError.ACCOUNT_FROZEN, PlatformError.PLATFORM_FROZEN, and four AuditEventType freeze constants added
- ClientFreezeService: freeze/unfreeze acquire pessimistic lock via findByIdForUpdate(), bulk-suspend/resume scheduled SMS per-client, publish typed audit events with SMS row counts
- PlatformFreezeService: freeze/unfreeze acquire pessimistic lock via findForUpdate(), platform-wide bulk-suspend/resume, null-safe shortfallAmount for admin vs. auto-triggered freezes
- 10 new unit tests (5 per service); test count grew from 177 to 187, all pass

## Task Commits

Each task was committed atomically:

1. **Task 1: Exception types, error codes, and AuditEventType constants** - `6648014` (feat)
2. **Task 2: ClientFreezeService and PlatformFreezeService with unit tests** - `f6d236d` (feat)

**Plan metadata:** (see final commit below)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/account/contract/ClientError.java` — added ACCOUNT_FROZEN constant
- `src/main/java/com/softropic/sendam/gateway/account/contract/AccountFrozenException.java` — new: extends ApplicationException with ClientError.ACCOUNT_FROZEN, carries clientId
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformError.java` — added PLATFORM_FROZEN constant
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformFrozenException.java` — new: extends ApplicationException with PlatformError.PLATFORM_FROZEN, no clientId
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java` — added CLIENT_ACCOUNT_FROZEN, CLIENT_ACCOUNT_UNFROZEN, PLATFORM_FROZEN, PLATFORM_UNFROZEN
- `src/main/java/com/softropic/sendam/gateway/account/service/ClientFreezeService.java` — new: freeze/unfreeze/isFrozen with pessimistic lock and audit
- `src/main/java/com/softropic/sendam/gateway/billing/service/PlatformFreezeService.java` — new: freeze/unfreeze/isFrozen with pessimistic lock and audit
- `src/test/java/com/softropic/sendam/gateway/account/service/ClientFreezeServiceTest.java` — new: 5 tests
- `src/test/java/com/softropic/sendam/gateway/billing/service/PlatformFreezeServiceTest.java` — new: 5 tests

## Decisions Made

- **Cross-module repo dependency:** ClientFreezeService in `account.service` directly injects `SendRequestRepository` from `sms.repo`. This is an intentional architectural decision documented in the class comment: the freeze service is a domain-level orchestrator that must bulk-update SMS state atomically within the same @Transactional boundary as the freeze state mutation. A wrapper service would add a layer without meaningful isolation.
- **PlatformFreezeService placement in billing.service:** Keeps `CreditReservationService` (also in `billing`) calling `isFrozen()` within the same module without introducing a cross-module service dependency.
- **Test assertion strategy for ApplicationException.getMessage():** Used `hasMessageContaining()` rather than exact string match because `ApplicationException.getMessage()` prepends `SUPPORT_ID: <sqid>, ERROR_CODE: <code>, MESSAGE:` — direct equals would be fragile.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 03 (REST controllers + CreditReservationService wiring) can proceed immediately:
- AccountFrozenException and PlatformFrozenException ready for ApiAdvice mapping (HTTP 423 / HTTP 503)
- ClientFreezeService.freeze()/unfreeze() ready for admin REST endpoints
- PlatformFreezeService.freeze()/unfreeze() ready for admin REST endpoints
- ClientFreezeService.isFrozen() and PlatformFreezeService.isFrozen() ready for CreditReservationService guard checks
- All 187 tests pass, BUILD SUCCESS

---
*Phase: 20-account-freeze-infrastructure*
*Completed: 2026-03-17*
