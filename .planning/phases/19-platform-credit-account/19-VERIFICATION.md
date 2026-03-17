---
phase: 19-platform-credit-account
verified: 2026-03-17T11:36:36Z
status: passed
score: 4/4 must-haves verified
gaps: []
---

# Phase 19: Platform Credit Account Verification Report

**Phase Goal:** Platform balance entity + ledger; admin records Nexah purchases; constrained top-up approval that debits platform balance.
**Verified:** 2026-03-17T11:36:36Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Admin can record a Nexah credit purchase; platform balance increases and a NEXAH_PURCHASE ledger entry is written | VERIFIED | `POST /api/admin/platform/credits/purchases` in `AdminPlatformCreditResource` delegates to `PlatformCreditService.recordNexahPurchase()`, which calls `applyLedgerEntry(NEXAH_PURCHASE, +amount, ...)` — acquires pessimistic lock, writes ledger entry, updates singleton balance row, returns `RecordNexahPurchaseResponse(newBalance, amountRecorded, recordedAt)` |
| 2 | Admin can query the current platform balance | VERIFIED | `GET /api/admin/platform/credits/balance` in `AdminPlatformCreditResource` delegates to `PlatformCreditService.getBalance()`, which calls `findBalance()` (no lock, read-only transaction) and returns `PlatformBalanceResponse(balance, asOf)` |
| 3 | Admin can browse platform ledger history filtered by entry type (NEXAH_PURCHASE / TOPUP_DEBIT / SHORTFALL_ABSORPTION) | VERIFIED | `GET /api/admin/platform/credits/ledger?type=&page=&size=` in `AdminPlatformCreditResource` delegates to `PlatformCreditService.getLedgerHistory(type, page, size)`; repository uses JPQL `WHERE (:type IS NULL OR e.entryType = :type)` — single query handles both filtered and unfiltered; returns `PlatformLedgerHistoryResponse`; all three enum values present in `PlatformLedgerEntryType` |
| 4 | Approving a client top-up atomically debits the platform balance; approval fails if platform balance would go negative | VERIFIED | `TopupService.approve()` calls `platformCreditService.applyLedgerEntry(TOPUP_DEBIT, -amount, topupId)` inside the same `@Transactional` propagated transaction, after the client credit write; `applyLedgerEntry` acquires `findForUpdate()` pessimistic lock, computes `newBalance`, throws `InsufficientPlatformBalanceException` if `newBalance < 0` causing full transaction rollback; `ApiAdvice` maps this to HTTP 422; `TopupServiceTest.approve_success` verifies the debit call with `verify(platformCreditService).applyLedgerEntry(eq(TOPUP_DEBIT), eq(-500L), eq("top_1"))` |

**Score:** 4/4 truths verified

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V11__platform_credit_account.sql` | DDL for both tables + singleton INSERT | VERIFIED | 36 lines; creates `platform_credit_balance` + `platform_credit_ledger_entry` tables; index on `(entry_type, created_date DESC)`; seeds singleton row `(id=1, balance=0)` |
| `src/main/java/.../billing/repo/PlatformCreditBalance.java` | JPA entity for singleton balance row | VERIFIED | 30 lines; `@Entity`, `@Table(schema="main")`, `balance` column, extends `AbstractAuditingEntity` |
| `src/main/java/.../billing/repo/PlatformCreditBalanceRepository.java` | Repository with `findForUpdate()` + `findBalance()` | VERIFIED | 22 lines; `findForUpdate()` uses `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(lock.timeout=2000)`; `findBalance()` is read-only query |
| `src/main/java/.../billing/repo/PlatformCreditLedgerEntry.java` | JPA entity for append-only ledger | VERIFIED | 52 lines; `entryType`, `amount`, `balanceAfter`, `reference` columns; `@Enumerated(STRING)` on `PlatformLedgerEntryType` |
| `src/main/java/.../billing/repo/PlatformCreditLedgerRepository.java` | Repository with `findByOptionalType()` | VERIFIED | 14 lines; JPQL optional-filter query with pagination; returns `Page<PlatformCreditLedgerEntry>` |
| `src/main/java/.../billing/contract/PlatformLedgerEntryType.java` | Enum: NEXAH_PURCHASE, TOPUP_DEBIT, SHORTFALL_ABSORPTION | VERIFIED | All three values present |
| `src/main/java/.../billing/contract/PlatformError.java` | ErrorCode enum: INSUFFICIENT_PLATFORM_BALANCE | VERIFIED | Implements `ErrorCode`, `getErrorCode()` returns `this.name()` |
| `src/main/java/.../billing/contract/InsufficientPlatformBalanceException.java` | Extends ApplicationException, holds currentBalance + requestedAmount | VERIFIED | Extends `ApplicationException`; constructor calls `super(message, PlatformError.INSUFFICIENT_PLATFORM_BALANCE)`; fields `currentBalance`, `requestedAmount` |
| `src/main/java/.../billing/contract/RecordNexahPurchaseRequest.java` | Record with @Positive amount, nullable reference | VERIFIED | `@Positive(message="amount must be positive")` on `long amount`; `String reference` nullable |
| `src/main/java/.../billing/contract/RecordNexahPurchaseResponse.java` | Record: newBalance, amountRecorded, recordedAt | VERIFIED | Three fields: `long newBalance`, `long amountRecorded`, `Instant recordedAt` |
| `src/main/java/.../billing/contract/PlatformBalanceResponse.java` | Record: balance, asOf | VERIFIED | `long balance`, `Instant asOf` |
| `src/main/java/.../billing/contract/PlatformLedgerEntryDto.java` | Record with @JsonProperty("balance_after") | VERIFIED | `@JsonProperty("balance_after")` on `balanceAfter` field |
| `src/main/java/.../billing/contract/PlatformLedgerHistoryResponse.java` | Record: entries, page, size, totalElements | VERIFIED | Four fields; `List<PlatformLedgerEntryDto> entries` |
| `src/main/java/.../billing/service/PlatformCreditService.java` | @Service @Transactional with four public methods | VERIFIED | 141 lines; class-level `@Transactional`; four public methods: `applyLedgerEntry`, `recordNexahPurchase`, `getBalance` (@Transactional(readOnly=true)), `getLedgerHistory` (@Transactional(readOnly=true)) |
| `src/main/java/.../billing/api/AdminPlatformCreditResource.java` | Three admin REST endpoints, @PreAuthorize ADMIN | VERIFIED | 75 lines; `@RestController`, `@RequestMapping("/api/admin/platform/credits")`, class-level `@PreAuthorize("hasRole('ADMIN')")`; three handlers: POST /purchases, GET /balance, GET /ledger |
| `src/main/java/.../security/config/AppEndpoints.java` (modified) | ADMIN_PLATFORM_CREDITS constant + SECURED_MAPPINGS entry | VERIFIED | `ADMIN_PLATFORM_CREDITS = "/api/admin/platform/credits/**"` declared; entry `Map.entry(ADMIN_PLATFORM_CREDITS, new String[]{AuthoritiesConstants.ADMIN})` in 14-entry `SECURED_MAPPINGS` |
| `src/main/java/.../security/api/ApiAdvice.java` (modified) | @ExceptionHandler for InsufficientPlatformBalanceException → HTTP 422 | VERIFIED | `@ExceptionHandler(InsufficientPlatformBalanceException.class)` + `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)` at line 403–408 |
| `src/main/java/.../audit/contract/AuditEventType.java` (modified) | NEXAH_PURCHASE_RECORDED enum constant | VERIFIED | Constant present at line 10 |
| `src/main/java/.../billing/service/TopupService.java` (modified) | PlatformCreditService injected; applyLedgerEntry(TOPUP_DEBIT) in approve() | VERIFIED | `PlatformCreditService platformCreditService` final field; `platformCreditService.applyLedgerEntry(TOPUP_DEBIT, -entity.getAmount(), topupId)` at lines 152–156 inside `approve()`, after client credit write, inside same `@Transactional` |
| `src/test/.../billing/service/TopupServiceTest.java` (modified) | @Mock PlatformCreditService; verify debit call in approve_success | VERIFIED | `@Mock private PlatformCreditService platformCreditService` at line 36; `verify(platformCreditService).applyLedgerEntry(eq(TOPUP_DEBIT), eq(-500L), eq("top_1"))` at line 91 |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AdminPlatformCreditResource.recordPurchase()` | `PlatformCreditService.recordNexahPurchase()` | Direct method call | WIRED | Controller injects service via `@RequiredArgsConstructor`; calls `platformCreditService.recordNexahPurchase(request)` |
| `PlatformCreditService.recordNexahPurchase()` | `PlatformCreditLedgerRepository.save()` + `PlatformCreditBalanceRepository.save()` | `applyLedgerEntry()` internal call | WIRED | `recordNexahPurchase` calls `applyLedgerEntry(NEXAH_PURCHASE, +amount, ...)` which saves ledger entry and updates balance row |
| `PlatformCreditService.applyLedgerEntry()` | `PlatformCreditBalanceRepository.findForUpdate()` | Pessimistic lock acquisition | WIRED | First call in `applyLedgerEntry` is `balanceRepository.findForUpdate()` with `@Lock(PESSIMISTIC_WRITE)` + 2000ms timeout |
| `AdminPlatformCreditResource.getBalance()` | `PlatformCreditService.getBalance()` | Direct method call | WIRED | Calls `platformCreditService.getBalance()`; returns `PlatformBalanceResponse` |
| `AdminPlatformCreditResource.getLedgerHistory()` | `PlatformCreditService.getLedgerHistory()` | Direct method call, optional type param | WIRED | Passes `@RequestParam(required=false) PlatformLedgerEntryType type`; service delegates to `ledgerRepository.findByOptionalType(type, pageable)` |
| `TopupService.approve()` | `PlatformCreditService.applyLedgerEntry(TOPUP_DEBIT)` | Same-transaction call | WIRED | Called after `creditService.applyLedgerEntry(..., TOPUP_APPROVED, ...)` inside `@Transactional` propagated transaction; negative amount; lock order documented in comment |
| `InsufficientPlatformBalanceException` | `ApiAdvice` | `@ExceptionHandler` | WIRED | `ApiAdvice` imports and handles the exception, returning HTTP 422 with `INSUFFICIENT_PLATFORM_BALANCE` error code |
| `ADMIN_PLATFORM_CREDITS` path | Filter chain (ROLE_ADMIN) | `SECURED_MAPPINGS` entry | WIRED | Registered as 14th entry in `Map.ofEntries` with `{AuthoritiesConstants.ADMIN}` authority array |

---

## Requirements Coverage

All seven PLAT requirements satisfied (per plan 03 completion summary):

| Requirement | Status | Notes |
|-------------|--------|-------|
| PLAT-01: NEXAH_PURCHASE_RECORDED audit + POST /purchases | SATISFIED | Audit type in `AuditEventType`; endpoint in controller |
| PLAT-02: Platform balance entity + GET /balance | SATISFIED | `PlatformCreditBalance` JPA entity + endpoint |
| PLAT-03: Platform ledger entity + GET /ledger | SATISFIED | `PlatformCreditLedgerEntry` JPA entity + endpoint with type filter |
| PLAT-04: Pessimistic lock on applyLedgerEntry | SATISFIED | `findForUpdate()` with PESSIMISTIC_WRITE + 2000ms timeout |
| PLAT-05: InsufficientPlatformBalanceException → HTTP 422 | SATISFIED | `ApiAdvice` handler at HTTP 422 |
| PLAT-06: TopupService.approve() atomic platform debit | SATISFIED | `applyLedgerEntry(TOPUP_DEBIT, -amount)` in same @Transactional |
| PLAT-07: Transaction rollback on insufficient platform balance | SATISFIED | `InsufficientPlatformBalanceException` is unchecked; Spring rolls back entire transaction including client credit write |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `InsufficientPlatformBalanceException.java` | 7 | Javadoc says "HTTP 400" but actual handler returns HTTP 422 | Warning | Documentation-only error; actual runtime behavior (HTTP 422) is correct as verified in `ApiAdvice` |

No TODO/FIXME/placeholder patterns found. No empty implementations. No stub handlers.

---

## Human Verification Required

None. All observable truths can be verified structurally:

- All endpoints are wired through to real DB operations (no mocked or hardcoded responses).
- The atomic rollback path is confirmed by test `TopupServiceTest.approve_success` verifying the platform debit call, and the exception being unchecked within a `@Transactional` context.
- Security is enforced at both filter chain (`SECURED_MAPPINGS`) and method-security (`@PreAuthorize`) levels.

The only item that would benefit from human testing is the pessimistic lock timeout behavior under concurrent load, which is an operational concern rather than a structural gap.

---

## Gaps Summary

No gaps. All four must-haves are fully achieved:

1. Nexah purchase recording: substantive implementation with pessimistic lock, ledger write, balance update, and correct response shape.
2. Balance query: read-only, non-locking, returns current balance and timestamp.
3. Ledger history with type filtering: optional-filter JPQL query, paginated, all three entry types present in enum.
4. Atomic top-up approval with platform debit and rollback guard: three-lock sequence documented and implemented; unchecked exception propagates rollback; test verifies the debit call is made.

Minor documentation defect noted: `InsufficientPlatformBalanceException` Javadoc incorrectly states "HTTP 400" — the actual handler correctly returns HTTP 422. This is a comment error with no runtime impact.

---

_Verified: 2026-03-17T11:36:36Z_
_Verifier: Claude (gsd-verifier)_
