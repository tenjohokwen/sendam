---
phase: 22-final-booking-segment-deviation
plan: 03
subsystem: testing
tags: [junit5, mockito, billing, final-booking, segment-deviation, tdd]

# Dependency graph
requires:
  - phase: 22-02
    provides: FinalBookingService (all 6 BOOK scenarios), SegmentDeviationService, SegmentDeviationAlertData record
provides:
  - FinalBookingServiceTest with 8 unit tests covering BOOK-01 through BOOK-06, zero-segments path, and SEGDEV per-recipient breakdown
affects:
  - phase-23: regression protection for any changes touching FinalBookingService or SegmentDeviationService
  - phase-24: confirms BOOK-05/06 freeze paths are correctly verified before admin deviation alert listing is built

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@MockitoSettings(LENIENT) on test class when @BeforeEach stubs are not consumed by all tests (early-return paths)"
    - "Mockito.mock(SendRequest.class) for entities with @Tsid id — builder cannot set id at test time; mock().thenReturn() is required"
    - "ArgumentCaptor<SegmentDeviationAlertData> pattern for asserting complex record fields after an interaction"
    - "stubClientBalance()/stubPlatformBalance() helper methods to reduce repetition across BOOK-04/05/06 tests"

key-files:
  created:
    - src/test/java/com/softropic/sendam/gateway/billing/service/FinalBookingServiceTest.java
  modified: []

key-decisions:
  - "@MockitoSettings(strictness = Strictness.LENIENT) added to suppress UnnecessaryStubbingException: book_zeroSegments_releasesReservation returns before consuming @BeforeEach stubs; book_book02_exactExpected_noAlert returns before buildBreakdown() consumes recipientRepository stub"
  - "Mockito.mock(SendRequest.class) used instead of builder: BaseEntity.id is set by @Tsid at persist time, not via Lombok builder; mock() + when(sendRequest.getId()).thenReturn(SEND_REQUEST_PK) is the correct test approach"

patterns-established:
  - "BOOK scenario test pattern: stub inputs, call book(), verify creditReservationService.debit/release, verify createAlert with argThat lambda checking key fields"
  - "Per-recipient breakdown test: set segmentsConsumed on built entities, capture alert data via ArgumentCaptor, assert breakdown size and per-entry fields"

# Metrics
duration: 15min
completed: 2026-03-17
---

# Phase 22 Plan 03: FinalBookingService Unit Tests Summary

**8 Mockito unit tests covering all 6 BOOK scenarios plus zero-segments release and SEGDEV per-recipient breakdown — 203 tests pass, all GREEN**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-17T15:11:00Z
- **Completed:** 2026-03-17T15:26:00Z
- **Tasks:** 1/1
- **Files modified:** 1 (1 created)

## Accomplishments

- All 8 named test methods from the plan specification written and passing
- BOOK-01: verifies debit(actual=3), SEGMENT alert with delta=-1, REFUNDED action, no extra debit, no freeze
- BOOK-02: verifies debit(actual=4), no alert created — exact match path
- BOOK-03: verifies debit(actual=5), SEGMENT alert with delta=+1, REFUNDED action — within-buffer deviation
- BOOK-04: verifies debit(reserved=6), SMS_EXTRA_DEBIT(-2), SEGMENT alert with EXTRA_DEBITED action, no freeze
- BOOK-05: verifies debit(reserved=6), drain client(-1), platform absorption(-1), clientFreezeService.freeze(), SHORTFALL_ABSORBED alert, no platform freeze
- BOOK-06: verifies debit(reserved=6), no client drain (balance=0), platform absorption(-1), both freeze() calls, two createAlert() invocations
- Zero-segments: verifies release(reservationId), no debit, no alert
- SEGDEV breakdown: ArgumentCaptor confirms 2 entries with expectedSegments=2, actualSegments=1, delta=-1 each

## Task Commits

Each task was committed atomically:

1. **Task 1: FinalBookingService unit tests — all 8 BOOK/SEGDEV scenarios** - `9b983c6` (test)

**Plan metadata:** (committed after SUMMARY.md and STATE.md update)

## Files Created/Modified

- `src/test/java/com/softropic/sendam/gateway/billing/service/FinalBookingServiceTest.java` - 322-line test class; 10 @Mock fields, @InjectMocks, @BeforeEach shared setup, 8 @Test methods, 2 helper stub methods

## Decisions Made

- **@MockitoSettings(LENIENT):** The `@BeforeEach` stubs `getId()`, `getRawExpectedCredits()`, `getReservedCredits()`, and `findBySendRequestIdFk()` on the sendRequest mock and recipientRepository. Two tests (`book_zeroSegments_releasesReservation` and `book_book02_exactExpected_noAlert`) return early before consuming all stubs — Mockito strict mode raises `UnnecessaryStubbingException` for these. LENIENT is the correct fix: the stubs are needed for the other 6 tests; removing them would require per-test setup duplication.
- **Mockito.mock(SendRequest.class) for getId():** SendRequest extends BaseEntity whose `id` field is populated by `@Tsid` at JPA persist time. Lombok `@SuperBuilder` does not set `id` (no `@Builder.Default`), so `SendRequest.builder().build().getId()` returns null. `Mockito.mock(SendRequest.class)` + `when(sendRequest.getId()).thenReturn(SEND_REQUEST_PK)` is the correct approach — consistent with the plan's implementation guidance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added @MockitoSettings(LENIENT) to resolve UnnecessaryStubbingException**

- **Found during:** Task 1 (initial test run — RED phase)
- **Issue:** `book_zeroSegments_releasesReservation` and `book_book02_exactExpected_noAlert` both triggered `UnnecessaryStubbingException` because the early-return paths do not consume all @BeforeEach stubs. Tests compiled but 2 of 8 failed.
- **Fix:** Added `@MockitoSettings(strictness = Strictness.LENIENT)` to the test class. Imports for `MockitoSettings` and `Strictness` were already present in the file.
- **Files modified:** `src/test/java/com/softropic/sendam/gateway/billing/service/FinalBookingServiceTest.java`
- **Verification:** `mvn test` — 203 tests, 0 failures, 0 errors, BUILD SUCCESS
- **Committed in:** `9b983c6` (task commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in test strictness configuration)
**Impact on plan:** Fix required for all 8 tests to pass; no scope creep; one-line addition to class annotation.

## Issues Encountered

None beyond the strictness fix documented above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Phase 22 (Final Booking & Segment Deviation) is now fully complete:
- All 3 plans executed: entities/migrations (22-01), service implementations (22-02), unit tests (22-03)
- 203 tests pass cleanly, including all 8 FinalBookingService scenarios
- BOOK-01 through BOOK-06, zero-segments release, and SEGDEV per-recipient breakdown are mechanically verified

Phase 23 can proceed. The billing finalization path is regression-protected.

---
*Phase: 22-final-booking-segment-deviation*
*Completed: 2026-03-17*
