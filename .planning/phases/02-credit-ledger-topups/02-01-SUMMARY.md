---
phase: 02-credit-ledger-topups
plan: "01"
subsystem: database
tags: [postgresql, flyway, jpa, hibernate, spring-data, ledger, credits]

# Dependency graph
requires:
  - phase: 01-client-api-key-auth
    provides: ClientEntity, ClientRepository, API key auth filter chain (@Order(1) on /v1/**)
provides:
  - Flyway V3 migration: client_credit_balance and credit_ledger_entry tables with indexes
  - ClientCreditBalance JPA entity (balance lock row, one per client)
  - ClientCreditBalanceRepository: findByClientId (non-locking) + findByClientIdForUpdate (PESSIMISTIC_WRITE)
  - CreditLedgerEntry JPA entity (append-only ledger, signed amount + balance_after)
  - CreditLedgerRepository: paginated findByClientIdOrderByCreatedDateDesc
  - LedgerEntryType enum (TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND)
  - CreditService: getBalance (O(1)), getLedgerHistory (paginated), applyLedgerEntry (locking write)
  - CreditResource: GET /v1/credits/balance, GET /v1/credits/ledger
  - InsufficientBalanceException (HTTP 400, error_code INSUFFICIENT_CLIENT_BALANCE)
  - ClientService extended: creates ClientCreditBalance(balance=0) atomically with client creation
affects:
  - 02-02 (TopupService calls applyLedgerEntry)
  - 02-03 (CreditReservationService calls applyLedgerEntry and findByClientIdForUpdate)
  - 03-sms-send (SMS billing uses credit ledger)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Ledger-first balance accounting: balance derived from ledger entries, never a mutable column alone
    - Balance lock row pattern: SELECT FOR UPDATE on client_credit_balance for concurrency control
    - Pessimistic locking via Spring Data @Lock(PESSIMISTIC_WRITE) with 2s timeout hint
    - Signed ledger amounts: positive = credit, negative = debit
    - balance_after written at insert time, not re-derived on read (O(1) audit trail)

key-files:
  created:
    - src/main/resources/db/migration/V3__credit_ledger.sql
    - src/main/java/com/softropic/sendam/client/repo/ClientCreditBalance.java
    - src/main/java/com/softropic/sendam/client/repo/ClientCreditBalanceRepository.java
    - src/main/java/com/softropic/sendam/client/repo/CreditLedgerEntry.java
    - src/main/java/com/softropic/sendam/client/repo/CreditLedgerRepository.java
    - src/main/java/com/softropic/sendam/client/contract/LedgerEntryType.java
    - src/main/java/com/softropic/sendam/client/contract/BalanceResponse.java
    - src/main/java/com/softropic/sendam/client/contract/LedgerEntryDto.java
    - src/main/java/com/softropic/sendam/client/contract/LedgerHistoryResponse.java
    - src/main/java/com/softropic/sendam/client/contract/exception/ClientError.java
    - src/main/java/com/softropic/sendam/client/contract/exception/InsufficientBalanceException.java
    - src/main/java/com/softropic/sendam/client/service/CreditService.java
    - src/main/java/com/softropic/sendam/client/api/CreditResource.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/ClientService.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java

key-decisions:
  - "ClientError enum owns INSUFFICIENT_CLIENT_BALANCE error code — follows pattern of SecError, ResourceError (each domain defines its own ErrorCode enum)"
  - "InsufficientBalanceException carries clientId, currentBalance, requestedAmount fields for structured logging by callers"
  - "size clamped to max 200 in getLedgerHistory — prevents runaway page size requests"
  - "TOPUP_PENDING entries with amount=0 pass through applyLedgerEntry without modifying balance — balance stays unchanged, entry is audit trail only"

patterns-established:
  - "CreditService.applyLedgerEntry: canonical write method for all credit mutations — TopupService and CreditReservationService must use this, never write directly to ledger"
  - "Balance lock row pattern: always acquire via findByClientIdForUpdate inside a @Transactional method before any credit mutation"

# Metrics
duration: 12min
completed: 2026-03-10
---

# Phase 2 Plan 01: Credit Ledger Foundation Summary

**Two Flyway-managed tables (client_credit_balance + credit_ledger_entry), four JPA artifacts, O(1) balance query, pessimistic-lock write method, and two live REST endpoints (GET /v1/credits/balance, GET /v1/credits/ledger)**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-10T14:00:00Z
- **Completed:** 2026-03-10T14:12:00Z
- **Tasks:** 2
- **Files modified:** 15 (13 created, 2 modified)

## Accomplishments
- Flyway V3 migration creates `client_credit_balance` (UNIQUE on client_id, balance lock row) and `credit_ledger_entry` (append-only, composite index on client_id + created_date DESC) tables
- `CreditService.applyLedgerEntry()` provides the single, concurrency-safe write path for all future credit mutations (Plans 02 and 03)
- Every new client automatically gets a `ClientCreditBalance(balance=0)` row created in the same transaction as `ClientService.createClient()` — no ResourceNotFoundException on first balance query
- `InsufficientBalanceException` handled globally by `ApiAdvice` with HTTP 400 and `error_code: "INSUFFICIENT_CLIENT_BALANCE"`

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migration V3 + JPA entities + repositories** - `33dc068` (feat)
2. **Task 2: CreditService, contract types, CreditResource, ClientService extension** - `9f98c59` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/db/migration/V3__credit_ledger.sql` - DDL for both tables + indexes
- `src/main/java/com/softropic/sendam/client/repo/ClientCreditBalance.java` - JPA entity (balance lock row)
- `src/main/java/com/softropic/sendam/client/repo/ClientCreditBalanceRepository.java` - findByClientId + findByClientIdForUpdate (PESSIMISTIC_WRITE)
- `src/main/java/com/softropic/sendam/client/repo/CreditLedgerEntry.java` - JPA entity (append-only ledger)
- `src/main/java/com/softropic/sendam/client/repo/CreditLedgerRepository.java` - paginated findByClientIdOrderByCreatedDateDesc
- `src/main/java/com/softropic/sendam/client/contract/LedgerEntryType.java` - enum: TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND
- `src/main/java/com/softropic/sendam/client/contract/BalanceResponse.java` - record with available_balance, unit, currency, last_updated_at
- `src/main/java/com/softropic/sendam/client/contract/LedgerEntryDto.java` - record for ledger entry projection
- `src/main/java/com/softropic/sendam/client/contract/LedgerHistoryResponse.java` - paginated ledger response record
- `src/main/java/com/softropic/sendam/client/contract/exception/ClientError.java` - ErrorCode enum for client module
- `src/main/java/com/softropic/sendam/client/contract/exception/InsufficientBalanceException.java` - extends ApplicationException with INSUFFICIENT_CLIENT_BALANCE error code
- `src/main/java/com/softropic/sendam/client/service/CreditService.java` - getBalance, getLedgerHistory, applyLedgerEntry
- `src/main/java/com/softropic/sendam/client/api/CreditResource.java` - GET /v1/credits/balance, GET /v1/credits/ledger
- `src/main/java/com/softropic/sendam/client/service/ClientService.java` - extended: inserts ClientCreditBalance row in createClient()
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java` - added @ExceptionHandler for InsufficientBalanceException → HTTP 400

## Decisions Made

- **ClientError enum for INSUFFICIENT_CLIENT_BALANCE:** Follows the established pattern where each domain defines its own `ErrorCode` enum (`SecError`, `ResourceError`). A new `ClientError` enum lives in `client.contract.exception` to avoid polluting the common exception layer with domain-specific codes.
- **InsufficientBalanceException carries structured fields:** `clientId`, `currentBalance`, `requestedAmount` are carried on the exception for use by future structured logging or monitoring. The message passed to `ApplicationException` is the human-readable summary.
- **size clamped to 200 in getLedgerHistory:** Prevents unbounded page sizes from becoming table scans; callers requesting more than 200 silently receive 200.
- **TOPUP_PENDING (amount=0) passes through applyLedgerEntry without altering balance:** When a top-up is initiated but not yet approved, the entry is an audit record only. The balance update comes later when TOPUP_APPROVED is applied.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `AbstractAuditingEntity` already carries the `status` field, so entities override it with `@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE` following the same pattern as `ClientEntity` and `ClientApiKeyEntity`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `CreditService.applyLedgerEntry(Long clientId, LedgerEntryType type, long amount, String reference)` is ready for Plan 02 (TopupService) and Plan 03 (CreditReservationService)
- `ClientCreditBalanceRepository.findByClientIdForUpdate` is available for Plan 03's reservation lock pattern
- Flyway migration will apply automatically on next `spring-boot:run` against a connected database
- No blockers for Plans 02 or 03

---
*Phase: 02-credit-ledger-topups*
*Completed: 2026-03-10*
