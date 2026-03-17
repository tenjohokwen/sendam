---
phase: 19-platform-credit-account
plan: 01
subsystem: database
tags: [flyway, jpa, postgresql, pessimistic-locking, billing, ledger]

# Dependency graph
requires:
  - phase: 15-topup-workflow
    provides: "CreditLedgerEntry / ClientCreditBalance / CreditLedgerRepository pattern to replicate"
  - phase: 10-base-entity
    provides: "AbstractAuditingEntity, EntityStatus, BaseEntity (@Tsid)"
provides:
  - "Flyway V11 migration: main.platform_credit_balance + main.platform_credit_ledger_entry tables + singleton INSERT"
  - "PlatformCreditBalance JPA entity (singleton lock row)"
  - "PlatformCreditBalanceRepository with findForUpdate() (PESSIMISTIC_WRITE, 2000ms timeout) and findBalance()"
  - "PlatformCreditLedgerEntry JPA entity (append-only ledger)"
  - "PlatformCreditLedgerRepository with findByOptionalType() paginated JPQL query"
  - "PlatformLedgerEntryType enum: NEXAH_PURCHASE, TOPUP_DEBIT, SHORTFALL_ABSORPTION"
affects:
  - 19-02-service
  - 19-03-rest-layer
  - 19-04-topup-integration

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Singleton lock row: one balance row per aggregate, identified by well-known id=1, SELECT FOR UPDATE on the full table (no WHERE)"
    - "PESSIMISTIC_WRITE + jakarta.persistence.lock.timeout=2000 on findForUpdate()"
    - "findByOptionalType JPQL: :type IS NULL OR e.entryType = :type — single paginated query covering filtered and unfiltered cases"
    - "PlatformLedgerEntryType placed in billing.contract — same package as LedgerEntryType"

key-files:
  created:
    - src/main/resources/db/migration/V11__platform_credit_account.sql
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditBalance.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditBalanceRepository.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditLedgerEntry.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditLedgerRepository.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerEntryType.java
  modified: []

key-decisions:
  - "Singleton row uses id=1 in Flyway INSERT — TSID-generated IDs encode timestamp bits at high-bit values far above 1; safe in practice. findForUpdate() uses no WHERE clause so the id value is never referenced by application code."
  - "PlatformLedgerEntryType created in Plan 01 alongside the entity to avoid compile errors — Plan 02 will NOT recreate it."
  - "findByOptionalType uses a single JPQL optional-filter query (not two derived methods) — consistent with plan spec and matches the pattern suggested in RESEARCH.md."

patterns-established:
  - "Singleton SELECT FOR UPDATE: @Query('SELECT b FROM PlatformCreditBalance b') with no WHERE — always fetches the one row"
  - "Optional-type paginated query: WHERE (:type IS NULL OR e.entryType = :type) ORDER BY e.createdDate DESC"

# Metrics
duration: 8min
completed: 2026-03-17
---

# Phase 19 Plan 01: Platform Credit Account — DB Foundation Summary

**Flyway V11 + four JPA files establishing the platform credit account schema: singleton balance lock row, append-only ledger, PESSIMISTIC_WRITE repository, and PlatformLedgerEntryType enum.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-17T13:56:45Z
- **Completed:** 2026-03-17T14:04:55Z
- **Tasks:** 2/2
- **Files created:** 6

## Accomplishments

- V11 migration creates both tables and seeds the singleton row (balance=0, id=1) in a single deterministic Flyway script — no application startup logic needed
- PlatformCreditBalanceRepository.findForUpdate() replicates the established SELECT FOR UPDATE pattern with 2000ms lock timeout, scoped to the full table (no WHERE clause required on a singleton)
- PlatformLedgerEntryType enum created now (alongside the entity) with all three required values including SHORTFALL_ABSORPTION, avoiding a future compile-time gap

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migration V11** - `2e1ed1c` (feat)
2. **Task 2: JPA entities and repositories** - `e18662e` (feat)

**Plan metadata:** `[final-commit-hash]` (docs: complete plan)

## Files Created

- `src/main/resources/db/migration/V11__platform_credit_account.sql` — DDL for both tables + index + singleton INSERT
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditBalance.java` — JPA entity for singleton balance row
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditBalanceRepository.java` — findForUpdate() + findBalance()
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditLedgerEntry.java` — JPA entity for append-only ledger
- `src/main/java/com/softropic/sendam/gateway/billing/repo/PlatformCreditLedgerRepository.java` — findByOptionalType() paginated query
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerEntryType.java` — enum with NEXAH_PURCHASE, TOPUP_DEBIT, SHORTFALL_ABSORPTION

## Decisions Made

- **Singleton id=1 in Flyway INSERT:** TSID-generated IDs encode a millisecond timestamp in their high bits; values this low are never produced naturally. The findForUpdate() query uses no WHERE clause so the concrete ID value is never referenced by application code. Safe choice.
- **PlatformLedgerEntryType in Plan 01:** Created alongside the entity rather than waiting for Plan 02 (which introduces the service layer). Avoids a compile error window and matches the plan's explicit instruction.
- **Single optional-filter JPQL query:** `WHERE (:type IS NULL OR e.entryType = :type)` — one method covers both the "all entries" and "by type" cases. Matches the plan spec and RESEARCH.md recommendation.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

`./mvnw` wrapper was unavailable (missing `.mvn/wrapper/maven-wrapper.properties`). Used system `mvn` from SDKMAN instead. Compile exited cleanly with no output.

## User Setup Required

None — no external service configuration required. Flyway will apply V11 automatically on next application startup.

## Next Phase Readiness

- All schema and JPA foundation is in place for Plan 02 (PlatformCreditService)
- PlatformLedgerEntryType already exists — Plan 02 must NOT recreate it
- findForUpdate() and findBalance() are ready for service-layer injection
- The singleton row will be inserted by V11 on first startup — no bootstrap code needed in Plan 02

---
*Phase: 19-platform-credit-account*
*Completed: 2026-03-17*
