---
phase: 19-platform-credit-account
plan: 02
subsystem: billing
tags: [spring, jpa, transactions, pessimistic-locking, billing, ledger, exception-handling]

# Dependency graph
requires:
  - phase: 19-01
    provides: "PlatformCreditBalance, PlatformCreditBalanceRepository (findForUpdate/findBalance), PlatformCreditLedgerEntry, PlatformCreditLedgerRepository (findByOptionalType), PlatformLedgerEntryType"
  - phase: 10-base-entity
    provides: "ApplicationException, ErrorCode, ResourceNotFoundException, EntityStatus"
provides:
  - "PlatformError enum: INSUFFICIENT_PLATFORM_BALANCE ErrorCode"
  - "InsufficientPlatformBalanceException extending ApplicationException (unchecked, rolls back @Transactional)"
  - "RecordNexahPurchaseRequest record with @Positive amount validation"
  - "RecordNexahPurchaseResponse record (newBalance, amountRecorded, recordedAt)"
  - "PlatformBalanceResponse record (balance, asOf)"
  - "PlatformLedgerEntryDto record with PlatformLedgerEntryType and balance_after"
  - "PlatformLedgerHistoryResponse record (entries, page, size, totalElements)"
  - "PlatformCreditService with applyLedgerEntry, recordNexahPurchase, getBalance, getLedgerHistory"
affects:
  - 19-03-rest-layer
  - 19-04-topup-integration

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "applyLedgerEntry is NOT @Transactional itself — class-level @Transactional covers it; callers (TopupService) propagate their transaction via default REQUIRED propagation"
    - "Three-lock order documented in comment: topup row [caller] → client credit balance [CreditService] → platform balance [PlatformCreditService] — must never be inverted"
    - "InsufficientPlatformBalanceException pattern: extends ApplicationException, takes error code in super(), adds domain fields (currentBalance, requestedAmount)"
    - "getLedgerHistory caps effectiveSize at 200 matching existing CreditService pattern"
    - "recordNexahPurchase reference fallback: request.reference() != null ? ... : 'nexah_purchase'"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformError.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/InsufficientPlatformBalanceException.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/RecordNexahPurchaseRequest.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/RecordNexahPurchaseResponse.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformBalanceResponse.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerEntryDto.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerHistoryResponse.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/PlatformCreditService.java
  modified: []

key-decisions:
  - "applyLedgerEntry has no @Transactional annotation — participates in caller's transaction via propagation=REQUIRED (class-level @Transactional covers it). TopupService.approve() must call this inside its own transaction so the topup row lock, credit balance update, and platform balance update all commit or all roll back together."
  - "InsufficientPlatformBalanceException does not carry clientId — platform balance is not per-client; currentBalance and requestedAmount are the relevant diagnostic fields."
  - "PlatformLedgerEntryDto uses @JsonProperty(\"balance_after\") for snake_case serialization, mirroring LedgerEntryDto convention."

patterns-established:
  - "Platform exception pattern: PlatformError enum implements ErrorCode; InsufficientPlatformBalanceException calls super(message, PlatformError.INSUFFICIENT_PLATFORM_BALANCE)"
  - "applyLedgerEntry lock-then-check pattern: findForUpdate() → compute newBalance → throw if < 0 → write entry → update row — same as CreditService.applyLedgerEntry"

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 19 Plan 02: Platform Credit Account — Service Layer Summary

**PlatformCreditService with pessimistic-lock write path, InsufficientPlatformBalanceException extending ApplicationException, and seven contract records/DTOs for the platform credit Admin API.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-17T11:21:15Z
- **Completed:** 2026-03-17T11:26:11Z
- **Tasks:** 2/2
- **Files created:** 8

## Accomplishments

- `applyLedgerEntry` acquires `findForUpdate()` pessimistic lock before any write, rejects negative results with `InsufficientPlatformBalanceException`, writes the ledger entry and updates the singleton balance row in a single transaction — documenting the three-lock order (topup row → client credit balance → platform balance) to prevent deadlocks in Plan 04
- All seven contract files compile cleanly with no dependencies on unbuilt classes; `InsufficientPlatformBalanceException` extends `ApplicationException` ensuring `ApiAdvice` handles it with `getSupportId()` and `getLogContext()`
- 177 existing tests all pass — no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Contract records and exception classes** - `4b7df34` (feat)
2. **Task 2: PlatformCreditService** - `ac97296` (feat)

**Plan metadata:** (docs commit below)

## Files Created

- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformError.java` — ErrorCode enum with INSUFFICIENT_PLATFORM_BALANCE
- `src/main/java/com/softropic/sendam/gateway/billing/contract/InsufficientPlatformBalanceException.java` — extends ApplicationException, fields: currentBalance, requestedAmount
- `src/main/java/com/softropic/sendam/gateway/billing/contract/RecordNexahPurchaseRequest.java` — record with @Positive amount, nullable reference
- `src/main/java/com/softropic/sendam/gateway/billing/contract/RecordNexahPurchaseResponse.java` — record: newBalance, amountRecorded, recordedAt
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformBalanceResponse.java` — record: balance, asOf (lastModifiedDate)
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerEntryDto.java` — record with @JsonProperty("balance_after"), PlatformLedgerEntryType
- `src/main/java/com/softropic/sendam/gateway/billing/contract/PlatformLedgerHistoryResponse.java` — record: entries, page, size, totalElements
- `src/main/java/com/softropic/sendam/gateway/billing/service/PlatformCreditService.java` — @Service @Transactional with four public methods

## Decisions Made

- **applyLedgerEntry has no @Transactional:** Class-level `@Transactional` covers it and default propagation=REQUIRED means callers (TopupService in Plan 04) propagate their transaction. This ensures topup row lock + client credit write + platform balance write all commit/rollback atomically.
- **InsufficientPlatformBalanceException without clientId:** Platform balance is not per-client; the diagnostic fields are `currentBalance` and `requestedAmount`. No clientId field needed.
- **@JsonProperty("balance_after") on PlatformLedgerEntryDto:** Mirrors `LedgerEntryDto` snake_case convention for consistent API response shape.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `PlatformCreditService.applyLedgerEntry()` is ready for Plan 03 REST controller to call `recordNexahPurchase` and `getBalance`/`getLedgerHistory`
- `applyLedgerEntry` is the internal write method Plan 04 (TopupService integration) will call with `TOPUP_DEBIT` type — lock order comment documents the required sequence
- All contract types are available for the REST layer to use as request/response bodies

---
*Phase: 19-platform-credit-account*
*Completed: 2026-03-17*
