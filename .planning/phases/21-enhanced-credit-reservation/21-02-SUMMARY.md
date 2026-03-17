---
phase: 21-enhanced-credit-reservation
plan: 02
subsystem: sms
tags: [sms, billing, credit-reservation, segments, gsm7, ucs2]

# Dependency graph
requires:
  - phase: 21-01
    provides: rawExpectedCredits column on send_request, expected_segments column on send_request_recipient (V13 migration + entity fields)
provides:
  - SmsService.sendSms() writes rawExpectedCredits and reservedCredits (buffered) to SendRequest
  - Each SendRequestRecipient row gets expectedSegments set to the calculated segment count
  - Reservation amount uses (expectedSegments + 1) * recipientCount buffer formula
  - Four RESV unit tests covering all four RESV requirements
affects:
  - 21-03 (REST API layer will expose rawExpectedCredits if needed)
  - 22 (deviation detection reads rawExpectedCredits vs actual segments consumed)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RESV buffer formula: reservationAmount = (expectedSegments + 1) * recipientCount"
    - "Dual-amount pattern: rawExpectedCredits (unbuffered) and reservedCredits (buffered) stored separately"
    - "Per-recipient expectedSegments: stored on each SendRequestRecipient for Phase 22 deviation comparison"

key-files:
  created: []
  modified:
    - src/main/java/com/softropic/sendam/gateway/sms/service/SmsService.java
    - src/test/java/com/softropic/sendam/gateway/sms/service/SmsServiceTest.java

key-decisions:
  - "reservationAmount formula uses (long)(expectedSegments + 1) * recipientCount — canonical form per plan spec"
  - "segmentCount field on SendRequest now set to expectedSegments (same formula, aligned naming) — semantics unchanged"
  - "Existing sendSms_success_immediate stub updated: 1 segment, 1 recipient now expects eq(2L) not eq(1L) — correct under RESV-02"
  - "BalanceResponse in new tests uses actual constructor (long availableBalance, String unit, String currency, Instant lastUpdatedAt) — plan template assumed a different signature; adapted to actual code"

patterns-established:
  - "RESV dual-amount: rawExpectedCredits and reservedCredits always populated together in SendRequest builder"
  - "expectedSegments propagated to every recipient row immediately after SendRequest persist"

# Metrics
duration: 15min
completed: 2026-03-17
---

# Phase 21 Plan 02: Enhanced Credit Reservation - Service Logic Summary

**SmsService.sendSms() now reserves (expectedSegments + 1) * recipientCount credits and stores both the buffered reservedCredits and unbuffered rawExpectedCredits on every SMS send, with expectedSegments propagated to each recipient row for Phase 22 deviation detection.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-17T13:45:00Z
- **Completed:** 2026-03-17T13:59:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Replaced flat `totalCredits = segmentCount * recipientCount` with three-value calculation: `expectedSegments`, `rawExpectedCredits`, `reservationAmount` (RESV-01, RESV-02, RESV-03)
- `SendRequest` builder now sets both `.reservedCredits(reservationAmount)` and `.rawExpectedCredits(rawExpectedCredits)` (RESV-02, RESV-03)
- Each `SendRequestRecipient` builder call sets `.expectedSegments(expectedSegments)` giving Phase 22 per-recipient deviation data (RESV-04)
- All 194 tests pass; four new RESV tests confirm all four requirements

## Task Commits

Each task was committed atomically:

1. **Task 1: Update SmsService.sendSms() with buffer formula and new field population** - `88511fe` (feat)
2. **Task 2: Add RESV unit tests to SmsServiceTest covering all four requirements** - `1ce29a1` (test)

**Plan metadata:** _(next commit — docs)_

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/sms/service/SmsService.java` - Steps 7-10 rewritten with buffer formula; Step 12 log updated
- `src/test/java/com/softropic/sendam/gateway/sms/service/SmsServiceTest.java` - ArgumentCaptor import added; existing stub corrected to eq(2L); four new RESV tests appended

## Decisions Made

- **reservationAmount formula:** `(long)(expectedSegments + 1) * recipientCount` — the canonical form from the plan spec; algebraically equivalent to `rawExpectedCredits + recipientCount` but the former is the authoritative expression
- **segmentCount field alignment:** `SendRequest.segmentCount` is now set to `expectedSegments` (same value, new variable name) — the field semantics are unchanged (per-message segment count, not total credits)
- **Existing test stub corrected (Rule 1 - Bug):** `sendSms_success_immediate` previously verified `eq(1L)` which was correct for the old formula but fails under RESV-02. Updated to `eq(2L)` for 1-segment, 1-recipient
- **BalanceResponse constructor adapted:** Plan template assumed `(Long clientId, long total, long reserved, long available)` but the actual record is `(long availableBalance, String unit, String currency, Instant lastUpdatedAt)`; new tests use the correct signature

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected existing test stub for sendSms_success_immediate**

- **Found during:** Task 2 (adding new RESV tests)
- **Issue:** The existing `verify(creditReservationService).reserve(eq(CLIENT_ID), eq(1L), ...)` was accurate for the old formula (1 segment * 1 recipient = 1). Under the RESV-02 formula this becomes (1+1)*1 = 2 so the stub would have thrown `WantedButNotInvoked` and all existing tests would have failed
- **Fix:** Updated verify to `eq(2L)` with an explanatory comment referencing RESV-02
- **Files modified:** `src/test/java/com/softropic/sendam/gateway/sms/service/SmsServiceTest.java`
- **Verification:** `mvn test -q` — 194 tests pass, no failures
- **Committed in:** `1ce29a1` (Task 2 commit)

**2. [Rule 1 - Bug] Adapted BalanceResponse constructor in new tests**

- **Found during:** Task 2 (writing new test methods)
- **Issue:** Plan suggested `new BalanceResponse(CLIENT_ID, 100L, 6L, 94L)` — but the actual record constructor is `(long availableBalance, String unit, String currency, Instant lastUpdatedAt)`
- **Fix:** New tests use `new BalanceResponse(94L, "units", "XAF", Instant.now())` matching the pattern from existing tests
- **Files modified:** `src/test/java/com/softropic/sendam/gateway/sms/service/SmsServiceTest.java`
- **Verification:** Compile succeeds; all tests pass
- **Committed in:** `1ce29a1` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes were necessary for correctness — the existing test would have broken and new tests would have failed to compile. No scope creep.

## Issues Encountered

None — plan executed cleanly after adapting for actual constructor signatures.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All four RESV requirements are satisfied and tested: 194 tests pass
- Phase 22 deviation detection can now read `rawExpectedCredits` from `SendRequest` and `expectedSegments` from each `SendRequestRecipient`
- 21-03 (REST API layer) is unblocked — no new API surface added in this plan

---
*Phase: 21-enhanced-credit-reservation*
*Completed: 2026-03-17*
