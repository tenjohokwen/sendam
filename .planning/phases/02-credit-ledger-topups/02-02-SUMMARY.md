---
phase: 02-credit-ledger-topups
plan: "02"
subsystem: payments
tags: [postgresql, flyway, jpa, hibernate, spring-data, topup, credits, pessimistic-locking, state-machine]

# Dependency graph
requires:
  - phase: 02-01
    provides: CreditService.applyLedgerEntry, ClientCreditBalance, LedgerEntryType, InsufficientBalanceException
  - phase: 01-client-api-key-auth
    provides: ClientEntity, ClientRepository, API key auth filter chain (@Order(1) on /v1/**)
provides:
  - Flyway V4 migration: topup_request table with UNIQUE(client_id, transaction_id) constraint
  - TopupRequestEntity JPA entity (separate topupStatus column distinct from inherited EntityStatus status)
  - TopupRequestRepository: findByClientIdAndId (client isolation) + findByIdForUpdate (PESSIMISTIC_WRITE)
  - TopupStatus enum: PENDING_APPROVAL, APPROVED, REJECTED
  - TopupService: createTopup, getTopupStatus, approve (TOPUP_APPROVED ledger entry), reject (no ledger entry)
  - POST /v1/credits/topups — 200 OK with topup_id in top_NNN format
  - GET /v1/credits/topups/{topup_id} — client-isolated status query
  - PUT /api/admin/topups/{topup_id}/approve — credits balance via applyLedgerEntry
  - PUT /api/admin/topups/{topup_id}/reject — no balance change
  - GET /api/admin/clients — all clients with current balance via JPQL LEFT JOIN
  - DuplicateTransactionIdException: HTTP 409, error_code DUPLICATE_TRANSACTION_ID
  - TopupAlreadyProcessedException: HTTP 409, error_code TOPUP_ALREADY_PROCESSED
affects:
  - 02-03 (CreditReservationService calls same applyLedgerEntry; tests can now fund a client balance via approve)
  - 03-sms-send (SMS billing debits funded client accounts)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Top-up state machine: PENDING_APPROVAL → APPROVED or REJECTED (one-way, no reversal)
    - Pessimistic lock on topup row during approve/reject to prevent double-processing race condition
    - flush-before-catch pattern: topupRepository.flush() inside try block forces UNIQUE constraint violation before the catch for DataIntegrityViolationException
    - TOPUP_PENDING (amount=0) is pure audit trail; balance unchanged — TOPUP_APPROVED (amount=+N) is the credit event

key-files:
  created:
    - src/main/resources/db/migration/V4__topup_request.sql
    - src/main/java/com/softropic/sendam/client/repo/TopupRequestEntity.java
    - src/main/java/com/softropic/sendam/client/repo/TopupRequestRepository.java
    - src/main/java/com/softropic/sendam/client/contract/TopupStatus.java
    - src/main/java/com/softropic/sendam/client/contract/CreateTopupRequest.java
    - src/main/java/com/softropic/sendam/client/contract/CreateTopupResponse.java
    - src/main/java/com/softropic/sendam/client/contract/TopupStatusResponse.java
    - src/main/java/com/softropic/sendam/client/contract/AdminClientDto.java
    - src/main/java/com/softropic/sendam/client/contract/exception/DuplicateTransactionIdException.java
    - src/main/java/com/softropic/sendam/client/contract/exception/TopupAlreadyProcessedException.java
    - src/main/java/com/softropic/sendam/client/service/TopupService.java
    - src/main/java/com/softropic/sendam/client/api/TopupResource.java
    - src/main/java/com/softropic/sendam/client/api/AdminTopupResource.java
  modified:
    - src/main/java/com/softropic/sendam/client/repo/ClientRepository.java
    - src/main/java/com/softropic/sendam/client/api/AdminClientResource.java
    - src/main/java/com/softropic/sendam/client/contract/exception/ClientError.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "flush-before-catch for duplicate transaction_id: topupRepository.flush() inside the try block forces Hibernate to flush pending SQL before the catch, ensuring the DataIntegrityViolationException is caught here rather than propagating from a later flush point"
  - "ADMIN_TOPUPS added to AppEndpoints.SECURED_MAPPINGS with ROLE_ADMIN only: /api/admin/topups/** was not in the map, so it fell through to the /api/** pattern (allowing USER and LTD_ADMIN). Added explicit entry mirroring the existing ADMIN_CLIENTS pattern"
  - "@PreAuthorize(hasRole('ADMIN')) belt-and-suspenders on AdminTopupResource: belt = SECURED_MAPPINGS filter chain restriction; suspenders = method-level annotation; both required per plan and defense-in-depth"
  - "No pagination on GET /api/admin/clients: admin-only endpoint, client count bounded for v1; JPQL ORDER BY createdDate DESC"

patterns-established:
  - "topupRepository.flush() inside try block for DB constraint detection: forces constraint check before leaving try, converts DataIntegrityViolationException to domain exception DuplicateTransactionIdException"
  - "TopupService is the single write path for top-up state transitions — no direct writes to topup_request from API layer"

# Metrics
duration: 18min
completed: 2026-03-10
---

# Phase 2 Plan 02: Top-Up Request Lifecycle Summary

**Full top-up lifecycle (submit/approve/reject) wired to CreditService.applyLedgerEntry — TOPUP_APPROVED atomically credits client balance via pessimistic-lock ledger write; duplicate transaction_id and double-approval both return 409 with domain-specific error codes**

## Performance

- **Duration:** 18 min
- **Started:** 2026-03-10T14:15:00Z
- **Completed:** 2026-03-10T14:33:00Z
- **Tasks:** 2
- **Files modified:** 18 (13 created, 5 modified)

## Accomplishments
- Full top-up state machine: PENDING_APPROVAL → APPROVED (balance credited via TOPUP_APPROVED ledger entry) or REJECTED (no ledger entry, no balance change)
- Pessimistic write lock on TopupRequestEntity during approve/reject prevents double-approval race condition
- Client isolation enforced on GET /v1/credits/topups/{topup_id} via findByClientIdAndId — clients cannot query other clients' top-ups
- GET /api/admin/clients returns all clients with live balance via JPQL LEFT JOIN (no N+1 problem)
- /api/admin/topups/** added to AppEndpoints SECURED_MAPPINGS with ROLE_ADMIN-only restriction (Rule 2 auto-fix — missing critical authorization coverage)

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway V4 migration, TopupRequestEntity, TopupStatus enum, contract types** - `99b4ee5` (feat)
2. **Task 2: TopupService, TopupResource, AdminTopupResource, AdminClientResource GET** - `c1f9f9a` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/db/migration/V4__topup_request.sql` - topup_request DDL with UNIQUE(client_id, transaction_id) and idx_topup_client_status
- `src/main/java/com/softropic/sendam/client/repo/TopupRequestEntity.java` - JPA entity; topupStatus (TopupStatus) and inherited status (EntityStatus) on separate columns
- `src/main/java/com/softropic/sendam/client/repo/TopupRequestRepository.java` - findByClientIdAndId + findByIdForUpdate (@Lock PESSIMISTIC_WRITE)
- `src/main/java/com/softropic/sendam/client/contract/TopupStatus.java` - PENDING_APPROVAL, APPROVED, REJECTED
- `src/main/java/com/softropic/sendam/client/contract/CreateTopupRequest.java` - POST body record with snake_case @JsonProperty
- `src/main/java/com/softropic/sendam/client/contract/CreateTopupResponse.java` - topup_id, status, requested_at
- `src/main/java/com/softropic/sendam/client/contract/TopupStatusResponse.java` - topup_id, amount, status, approved_at
- `src/main/java/com/softropic/sendam/client/contract/AdminClientDto.java` - id, name, EntityStatus, balance
- `src/main/java/com/softropic/sendam/client/contract/exception/DuplicateTransactionIdException.java` - extends ApplicationException, error_code DUPLICATE_TRANSACTION_ID
- `src/main/java/com/softropic/sendam/client/contract/exception/TopupAlreadyProcessedException.java` - extends ApplicationException, error_code TOPUP_ALREADY_PROCESSED
- `src/main/java/com/softropic/sendam/client/service/TopupService.java` - createTopup, getTopupStatus, approve, reject
- `src/main/java/com/softropic/sendam/client/api/TopupResource.java` - POST /v1/credits/topups, GET /v1/credits/topups/{topup_id}
- `src/main/java/com/softropic/sendam/client/api/AdminTopupResource.java` - PUT /api/admin/topups/{topup_id}/approve + reject with @PreAuthorize
- `src/main/java/com/softropic/sendam/client/repo/ClientRepository.java` - added findAllWithBalance() JPQL LEFT JOIN query
- `src/main/java/com/softropic/sendam/client/api/AdminClientResource.java` - added GET /api/admin/clients
- `src/main/java/com/softropic/sendam/client/contract/exception/ClientError.java` - added DUPLICATE_TRANSACTION_ID, TOPUP_ALREADY_PROCESSED
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java` - added handlers for DuplicateTransactionIdException and TopupAlreadyProcessedException (409)
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - added ADMIN_TOPUPS with ROLE_ADMIN restriction

## Decisions Made

- **flush-before-catch for duplicate transaction_id detection:** `topupRepository.flush()` inside the `try` block forces Hibernate to flush pending SQL before the `catch`. Without this, Hibernate may batch the INSERT and flush later (outside the try), causing `DataIntegrityViolationException` to propagate up unhandled. The flush makes the constraint violation deterministic.
- **ADMIN_TOPUPS added to AppEndpoints:** `/api/admin/topups/**` was missing from `SECURED_MAPPINGS`, causing it to fall through to the `/api/**` pattern which allows USER and LTD_ADMIN. Added explicit mapping with `ROLE_ADMIN` only, mirroring the existing `ADMIN_CLIENTS` pattern. This was a missing critical authorization gap (deviation Rule 2).
- **@PreAuthorize belt-and-suspenders on AdminTopupResource:** Method-level annotation in addition to filter chain restriction. Defense-in-depth: if someone inadvertently removes or misconfigures the filter chain entry, the method annotation still blocks non-ADMIN access.
- **No pagination on GET /api/admin/clients:** Admin-only endpoint with bounded client count in v1. JPQL `ORDER BY c.createdDate DESC` provides stable ordering.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added /api/admin/topups/** to AppEndpoints SECURED_MAPPINGS with ROLE_ADMIN-only restriction**
- **Found during:** Task 2 (AdminTopupResource creation)
- **Issue:** `/api/admin/topups/**` was not in `SECURED_MAPPINGS`. It fell through to the `/api/**` catch-all pattern which grants access to `ROLE_ADMIN`, `ROLE_USER`, and `ROLE_LTD_ADMIN`. Any authenticated user could call approve/reject.
- **Fix:** Added `ADMIN_TOPUPS = "/api/admin/topups/**"` constant and entry in `SECURED_MAPPINGS` with `new String[]{AuthoritiesConstants.ADMIN}`, mirroring the existing `ADMIN_CLIENTS` pattern.
- **Files modified:** `AppEndpoints.java`
- **Verification:** Compile passes; restriction follows exact same pattern as the pre-existing ADMIN_CLIENTS entry.
- **Committed in:** `c1f9f9a` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (missing critical authorization)
**Impact on plan:** Essential security fix. No scope creep — same pattern already established for ADMIN_CLIENTS.

## Issues Encountered

None. The flush-before-catch pattern for `DataIntegrityViolationException` was identified during design review of `createTopup()` before writing the method — pre-empted rather than discovered as a runtime failure.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `TopupService.approve(topupId)` is ready for integration tests in Plan 03 to fund a client's balance
- `CreditService.applyLedgerEntry` continues to be the single write path — Plan 03's `CreditReservationService` uses it for SMS_RESERVATION, SMS_DEBIT, SMS_REFUND entries
- All five Phase 2 financial requirements (TOPUP-01, TOPUP-02, TOPUP-03, ADMIN-02, ADMIN-03) are now testable end-to-end
- No blockers for Plan 03

---
*Phase: 02-credit-ledger-topups*
*Completed: 2026-03-10*
