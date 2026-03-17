---
phase: 20-account-freeze-infrastructure
plan: 03
subsystem: api
tags: [spring-boot, rest, freeze, account-management, billing, credit-reservation, exception-handling, security]

# Dependency graph
requires:
  - phase: 20-01
    provides: PlatformFreezeState entity, SendRequestStatus.SUSPENDED, freeze-related DB schema (V12 migrations)
  - phase: 20-02
    provides: ClientFreezeService, PlatformFreezeService, AccountFrozenException, PlatformFrozenException, unit tests
  - phase: 19-03
    provides: AdminPlatformCreditResource pattern; AppEndpoints/ApiAdvice conventions established
provides:
  - AdminClientFreezeResource: PUT /api/admin/clients/{clientId}/freeze and /unfreeze HTTP endpoints
  - AdminPlatformFreezeResource: POST /api/admin/platform/freeze and DELETE /api/admin/platform/freeze HTTP endpoints
  - 6 contract records (ClientFreezeRequest/Unfreeze/Response, PlatformFreezeRequest/Unfreeze/Response)
  - AppEndpoints: ADMIN_CLIENT_FREEZE and ADMIN_PLATFORM_FREEZE constants; SECURED_MAPPINGS grows to 16
  - ApiAdvice: AccountFrozenException (HTTP 403 ACCOUNT_FROZEN) and PlatformFrozenException (HTTP 503 PLATFORM_FROZEN) handlers
  - CreditReservationService.reserve(): client freeze guard then platform freeze guard before balance lock
  - SmsService.cancelScheduled(): permits SUSPENDED status in addition to ACCEPTED
  - 3 new CreditReservationService unit tests (total 10); project-wide 190 passing tests
affects: [21-suspension-scheduler, 22-low-balance-trigger, frontend-freeze-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - belt-and-suspenders: @PreAuthorize on controller + AppEndpoints URL filter chain for ADMIN endpoints
    - short-circuit freeze guard: isFrozen() non-locking read before SELECT FOR UPDATE balance lock in reserve()
    - SUSPENDED cancel extension: additive status check (statusAllowed boolean) rather than replacing the ACCEPTED check

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/account/contract/ClientFreezeRequest.java
    - src/main/java/com/softropic/sendam/gateway/account/contract/ClientUnfreezeRequest.java
    - src/main/java/com/softropic/sendam/gateway/account/contract/ClientFreezeResponse.java
    - src/main/java/com/softropic/sendam/gateway/account/api/AdminClientFreezeResource.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformFreezeRequest.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformUnfreezeRequest.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformFreezeResponse.java
    - src/main/java/com/softropic/sendam/gateway/billing/api/AdminPlatformFreezeResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/CreditReservationService.java
    - src/main/java/com/softropic/sendam/gateway/sms/service/SmsService.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/CreditReservationServiceTest.java

key-decisions:
  - "AdminClientFreezeResource uses class-level @PreAuthorize('hasRole(ADMIN)') — both endpoints share the same authority; consistent with AdminPlatformCreditResource pattern"
  - "ADMIN_CLIENT_FREEZE = /api/admin/clients/*/freeze/** added alongside existing ADMIN_CLIENTS — belt-and-suspenders specificity consistent with ADMIN_API_KEYS alongside ADMIN_CLIENTS"
  - "CreditReservationService.reserve() checks clientFreezeService.isFrozen() (non-locking read) before platformFreezeService.isFrozen() before SELECT FOR UPDATE balance lock — freeze is a pre-lock short-circuit; brief race window acceptable (documented in code)"
  - "CreditReservationService cross-module import of ClientFreezeService (account.service) documented in class Javadoc — service-to-service injection is permitted; the prohibition is on repo-level cross-module imports"

patterns-established:
  - "Freeze guard pattern: isFrozen() non-locking reads before expensive SELECT FOR UPDATE acquires; both are short-circuits that skip DB entirely"
  - "SUSPENDED cancel extension: additive boolean (statusAllowed = ACCEPTED || SUSPENDED) avoids replacing existing check, preserves readability"

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 20 Plan 03: REST API Layer and Reservation Guards Summary

**Admin freeze HTTP endpoints (client + platform), ApiAdvice handlers (403/503), CreditReservationService freeze short-circuits, and SUSPENDED cancel support — all 10 CFREEZE/PFLAT requirements satisfied**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-17T12:14:27Z
- **Completed:** 2026-03-17T12:19:19Z
- **Tasks:** 2/2
- **Files modified:** 13 (8 created, 5 modified)

## Accomplishments
- All 4 admin freeze endpoints wired: PUT /{clientId}/freeze, PUT /{clientId}/unfreeze, POST /platform/freeze, DELETE /platform/freeze
- AccountFrozenException (HTTP 403 ACCOUNT_FROZEN) and PlatformFrozenException (HTTP 503 PLATFORM_FROZEN) registered in ApiAdvice
- CreditReservationService.reserve() now short-circuits on client or platform freeze before acquiring the balance lock
- SmsService.cancelScheduled() extended to allow SUSPENDED scheduled SMS to be cancelled by clients
- 3 new unit tests covering all freeze guard paths; project total 190 passing tests

## Task Commits

Each task was committed atomically:

1. **Task 1: Contract DTOs, REST controllers, AppEndpoints, and ApiAdvice** - `5cfef15` (feat)
2. **Task 2: CreditReservationService freeze guards + SmsService cancel-SUSPENDED + CreditReservationServiceTest update** - `89ac885` (feat)

**Plan metadata:** see docs commit below

## Files Created/Modified
- `gateway/account/contract/ClientFreezeRequest.java` — @NotBlank reason record for PUT .../freeze
- `gateway/account/contract/ClientUnfreezeRequest.java` — @NotBlank resolution record for PUT .../unfreeze
- `gateway/account/contract/ClientFreezeResponse.java` — clientId + frozen + message response record
- `gateway/account/api/AdminClientFreezeResource.java` — PUT /{clientId}/freeze and /{clientId}/unfreeze; @PreAuthorize ADMIN
- `gateway/billing/contract/PlatformFreezeRequest.java` — @NotBlank reason record for POST /platform/freeze
- `gateway/billing/contract/PlatformUnfreezeRequest.java` — @NotBlank resolution record for DELETE /platform/freeze
- `gateway/billing/contract/PlatformFreezeResponse.java` — frozen + message response record
- `gateway/billing/api/AdminPlatformFreezeResource.java` — POST /freeze and DELETE /freeze; @PreAuthorize ADMIN
- `security/config/AppEndpoints.java` — ADMIN_CLIENT_FREEZE, ADMIN_PLATFORM_FREEZE constants; SECURED_MAPPINGS 14 → 16
- `security/api/ApiAdvice.java` — accountFrozenHandler (403) and platformFrozenHandler (503)
- `gateway/billing/service/CreditReservationService.java` — inject ClientFreezeService + PlatformFreezeService; isFrozen() short-circuits before balance lock
- `gateway/sms/service/SmsService.java` — cancelScheduled() accepts ACCEPTED || SUSPENDED (CFREEZE-04)
- `gateway/billing/service/CreditReservationServiceTest.java` — 3 new freeze guard tests (total 10)

## Decisions Made
- AdminClientFreezeResource uses class-level `@PreAuthorize("hasRole('ADMIN')")` — both endpoints share the same authority; consistent with AdminPlatformCreditResource pattern (19-03 precedent)
- `ADMIN_CLIENT_FREEZE = /api/admin/clients/*/freeze/**` added alongside existing `ADMIN_CLIENTS` — belt-and-suspenders specificity consistent with how `ADMIN_API_KEYS` is declared alongside `ADMIN_CLIENTS`
- CreditReservationService.reserve() orders checks: client freeze first, platform freeze second, balance lock last — client-specific check is cheaper and fails faster for the common per-client case
- Cross-module service import of ClientFreezeService (account.service) into CreditReservationService (billing.service) documented in class Javadoc — service-to-service injection is permitted per ARCHITECTURE.md
- SmsService.cancelScheduled(): additive `statusAllowed` boolean (ACCEPTED || SUSPENDED) rather than replacing the ACCEPTED check — preserves original semantics and is self-documenting with CFREEZE-04 comment

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 10 CFREEZE and PFLAT requirements are now satisfied end-to-end
- Phase 20 is complete: schema (Plan 01), services + exception types (Plan 02), REST API + guards (Plan 03)
- Phase 21 (suspension scheduler) can reference `SendRequestStatus.SUSPENDED` and `AdminClientFreezeResource`/`AdminPlatformFreezeResource` endpoint contracts
- Phase 22 (low-balance automatic trigger) can call `PlatformFreezeService.freeze(reason, shortfallAmount)` directly — shortfallAmount parameter already accepted

---
*Phase: 20-account-freeze-infrastructure*
*Completed: 2026-03-17*
