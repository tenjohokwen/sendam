---
phase: 19-platform-credit-account
plan: 03
subsystem: billing
tags: [spring, rest, security, exception-handling, billing, ledger, transactions, pessimistic-locking]

# Dependency graph
requires:
  - phase: 19-02
    provides: "PlatformCreditService (recordNexahPurchase, getBalance, getLedgerHistory, applyLedgerEntry), InsufficientPlatformBalanceException, all contract DTOs/records"
  - phase: 19-01
    provides: "PlatformCreditBalance, PlatformCreditLedgerEntry, repositories, PlatformLedgerEntryType"
provides:
  - "AdminPlatformCreditResource: three admin REST endpoints under /api/admin/platform/credits/"
  - "AppEndpoints.ADMIN_PLATFORM_CREDITS constant + SECURED_MAPPINGS entry restricting to ROLE_ADMIN"
  - "ApiAdvice @ExceptionHandler for InsufficientPlatformBalanceException -> HTTP 422 UNPROCESSABLE_ENTITY"
  - "AuditEventType.NEXAH_PURCHASE_RECORDED enum constant"
  - "TopupService.approve() debits platform balance atomically in same @Transactional transaction as client credit"
affects:
  - 19-04-topup-integration (if planned — TopupService.approve() integration is now complete)
  - phase-20-onwards (all seven PLAT requirements satisfied)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Belt-and-suspenders admin protection: filter chain (SECURED_MAPPINGS) + @PreAuthorize(\"hasRole('ADMIN')\") on controller class"
    - "Three-lock atomic approve: topup row (findByIdForUpdate) -> client credit (CreditService.applyLedgerEntry) -> platform balance (PlatformCreditService.applyLedgerEntry) — all in one @Transactional propagated from TopupService.approve()"
    - "InsufficientPlatformBalanceException rolls back client credit write: thrown inside same transaction context; Spring @Transactional rolls back all writes on unchecked exception"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/api/AdminPlatformCreditResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/TopupService.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/TopupServiceTest.java

key-decisions:
  - "AdminPlatformCreditResource uses class-level @PreAuthorize(\"hasRole('ADMIN')\") rather than per-method — all three endpoints share the same authority requirement; consistent with other admin resources"
  - "HTTP 422 (UNPROCESSABLE_ENTITY) for InsufficientPlatformBalanceException distinguishes platform balance shortfall from HTTP 400 InsufficientBalanceException (client balance); 422 semantics: request was syntactically valid but cannot be processed given current state"
  - "NEXAH_PURCHASE_RECORDED placed after TOPUP_REJECTED in AuditEventType — logically groups platform-level admin events near topup events"

patterns-established:
  - "Platform admin controller pattern: @RestController + @RequestMapping + class-level @PreAuthorize + @RequiredArgsConstructor + @Slf4j; mirrors AdminTopupResource"
  - "TopupService.approve() three-lock atomic sequence is now complete and documented in both source comment and Javadoc"

# Metrics
duration: 15min
completed: 2026-03-17
---

# Phase 19 Plan 03: Platform Credit Account — REST Layer Summary

**Three admin REST endpoints for platform credit (record Nexah purchase, query balance, browse ledger), security wiring, HTTP 422 exception handler for InsufficientPlatformBalanceException, and TopupService.approve() modified to atomically debit platform balance.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-17T11:30:00Z
- **Completed:** 2026-03-17T11:45:00Z
- **Tasks:** 2/2
- **Files created:** 1
- **Files modified:** 5

## Accomplishments

- `AdminPlatformCreditResource` exposes `POST /purchases`, `GET /balance`, `GET /ledger` under `/api/admin/platform/credits/` — all three endpoints protected by ROLE_ADMIN at both filter chain and method-security levels
- `AppEndpoints.ADMIN_PLATFORM_CREDITS` constant added and registered in `SECURED_MAPPINGS` as the 14th entry (Map.ofEntries varargs — no limit)
- `ApiAdvice` maps `InsufficientPlatformBalanceException` to HTTP 422 with error code `INSUFFICIENT_PLATFORM_BALANCE` — distinct from HTTP 400 `InsufficientBalanceException` (client balance), enabling callers to distinguish platform vs. client shortfalls
- `TopupService.approve()` now calls `platformCreditService.applyLedgerEntry(TOPUP_DEBIT, -amount, topupId)` immediately after the client credit write, inside the same `@Transactional` propagated transaction — if platform balance is insufficient the entire transaction (including the client credit write) rolls back atomically; lock order comment and Javadoc updated
- All 177 existing tests continue to pass; `TopupServiceTest.approve_success` updated to mock `PlatformCreditService` and verify the platform debit call

## Task Commits

Each task was committed atomically:

1. **Task 1: AdminPlatformCreditResource + security wiring + ApiAdvice + AuditEventType** - `32947e3` (feat)
2. **Task 2: Modify TopupService.approve() to atomically debit platform balance** - `4308039` (feat)

**Plan metadata:** (docs commit below)

## Files Created

- `src/main/java/com/softropic/sendam/gateway/billing/api/AdminPlatformCreditResource.java` — three admin endpoints; class-level @PreAuthorize; delegates to PlatformCreditService

## Files Modified

- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` — ADMIN_PLATFORM_CREDITS constant + 14th SECURED_MAPPINGS entry
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java` — import + @ExceptionHandler for InsufficientPlatformBalanceException → HTTP 422
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java` — NEXAH_PURCHASE_RECORDED constant added
- `src/main/java/com/softropic/sendam/gateway/billing/service/TopupService.java` — PlatformCreditService injected; applyLedgerEntry(TOPUP_DEBIT) call inserted in approve(); lock order comment and Javadoc updated
- `src/test/java/com/softropic/sendam/gateway/billing/service/TopupServiceTest.java` — @Mock PlatformCreditService added; verify platform debit call added to approve_success test

## Decisions Made

- **Class-level @PreAuthorize on AdminPlatformCreditResource:** All three endpoints require ROLE_ADMIN; placing the annotation on the class avoids repetition and prevents accidental omission on future endpoints added to this controller.
- **HTTP 422 for InsufficientPlatformBalanceException:** 422 Unprocessable Entity signals "valid request, cannot complete given current state" — appropriate for a balance check failure. Keeps it distinct from HTTP 400 (client balance, `InsufficientBalanceException`) allowing API clients to programmatically distinguish the two error conditions.
- **NEXAH_PURCHASE_RECORDED placed after TOPUP_REJECTED:** Logical grouping — follows the topup lifecycle events; no functional impact on enum ordering.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TopupServiceTest.approve_success NPE on PlatformCreditService**

- **Found during:** Task 2 verification (test run)
- **Issue:** `TopupServiceTest` used `@InjectMocks` and three `@Mock` fields. After injecting `PlatformCreditService` into `TopupService`, Mockito could not inject it because no `@Mock` field existed — `platformCreditService` was null at test runtime, causing `NullPointerException` in `approve_success`.
- **Fix:** Added `@Mock private PlatformCreditService platformCreditService;` to the test class. Also added `verify(platformCreditService).applyLedgerEntry(eq(PlatformLedgerEntryType.TOPUP_DEBIT), eq(-500L), eq("top_1"))` to `approve_success` to assert the platform debit actually occurs.
- **Files modified:** `src/test/java/com/softropic/sendam/gateway/billing/service/TopupServiceTest.java`
- **Verification:** 177/177 tests pass after fix.
- **Committed in:** `4308039` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug: test NPE due to missing mock)
**Impact on plan:** Necessary for test suite correctness. No scope creep; the test was already testing the right behavior, just missing the mock injection for the new dependency.

## Issues Encountered

None beyond the test NPE documented above as a deviation.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All seven PLAT requirements (PLAT-01 through PLAT-07) are now satisfied:
  - PLAT-01: `NEXAH_PURCHASE_RECORDED` audit type + `POST /purchases` endpoint
  - PLAT-02: Platform balance entity (Plan 01) + `GET /balance` endpoint
  - PLAT-03: Platform ledger entity (Plan 01) + `GET /ledger` endpoint
  - PLAT-04: `PlatformCreditService.applyLedgerEntry` pessimistic lock (Plan 02)
  - PLAT-05: `InsufficientPlatformBalanceException` → HTTP 422 (this plan)
  - PLAT-06: `TopupService.approve()` atomic debit (this plan)
  - PLAT-07: Transaction rollback when platform balance insufficient (this plan — same @Transactional)
- Phase 19 is complete — ready for Phase 20 or the next milestone phase
- No blockers or concerns

---
*Phase: 19-platform-credit-account*
*Completed: 2026-03-17*
