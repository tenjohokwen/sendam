---
phase: 24-deviation-alert-management
plan: 03
subsystem: api
tags: [spring-boot, jpa, service, billing, deviation-alerts, status-lifecycle, mockito]

# Dependency graph
requires:
  - phase: 24-02
    provides: DeviationAlertDto, DeviationAlertEventDto, AlertStatusTransitionException, DeviationAlertError, findByOptionalFilters on both repos
  - phase: 24-01
    provides: AlertStatus.canTransitionTo(), DeviationAlertEvent entity, DeviationAlertEventRepository
  - phase: 22-01
    provides: SegmentDeviationAlert entity and SegmentDeviationAlertRepository
  - phase: 23-01
    provides: BalanceDeviationAlert entity and BalanceDeviationAlertRepository
provides:
  - DeviationAlertManagementService with listAlerts, getAlert, acknowledge, resolve operations
  - OPEN→ACKNOWLEDGED→RESOLVED lifecycle enforcement via AlertStatus.canTransitionTo()
  - Merge-and-sort logic for cross-repo listing (type=null case)
  - DeviationAlertEvent persistence with exactly one FK set (segmentAlertIdFk or balanceAlertIdFk)
  - actedBy populated from SecurityUtil.getCurrentUserName()
  - 7 unit tests covering all service paths (213 total tests)
affects: [24-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Merge-and-sort pattern: both repos queried with Pageable.unpaged(), results merged into ArrayList, sorted by createdDate DESC with Comparator.nullsLast(reverseOrder()), then manually sliced via PageImpl(subList, pageable, total)"
    - "Route-by-type pattern: single service method dispatches to segmentRepo or balanceRepo based on DeviationAlertType param; null type queries both"
    - "Single-FK-per-row event pattern: DeviationAlertEvent sets exactly one of segmentAlertIdFk or balanceAlertIdFk; the other remains null — prevents cross-type FK ambiguity"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/service/DeviationAlertManagementService.java
    - src/test/java/com/softropic/sendam/gateway/billing/service/DeviationAlertManagementServiceTest.java
  modified: []

key-decisions:
  - "guardTransition uses AlertStatus.canTransitionTo() before writing — single guard call shared by acknowledge and resolve; throws AlertStatusTransitionException with alertId and currentStatus in logContext"
  - "saveEvent helper centralises DeviationAlertEvent construction — balanceAlertIdFk/segmentAlertIdFk passed as explicit nullable params; only one is non-null per call"
  - "listAlerts null-type merge uses Comparator.nullsLast(reverseOrder()) — handles edge case where createdDate is null (shouldn't occur in practice but safe)"

patterns-established:
  - "Route-by-type dispatch: if (type == BALANCE) → balanceRepo; else if (type == SEGMENT || PLATFORM_FREEZE) → segmentRepo; else → merge both"
  - "@MockitoSettings(LENIENT) on test class — consistent with 22-03 decision; @BeforeEach stubs not used in all paths; prevents unnecessary strictness failures"
  - "Mockito.mock(Entity.class) for @Tsid entities — builder cannot set id at test time; when(entity.getId()).thenReturn(pk) is the required pattern (22-03 decision)"

# Metrics
duration: 8min
completed: 2026-03-17
---

# Phase 24 Plan 03: DeviationAlertManagementService Summary

**OPEN→ACKNOWLEDGED→RESOLVED lifecycle service with cross-repo merge-and-sort for type=null listing, guardTransition via AlertStatus.canTransitionTo(), and DeviationAlertEvent persistence with single FK per row — 7 unit tests, 213 total passing**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-17T16:35:01Z
- **Completed:** 2026-03-17T16:43:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Implemented `DeviationAlertManagementService` with four public methods: `listAlerts`, `getAlert`, `acknowledge`, `resolve`
- `listAlerts` routes by type — BALANCE-only, SEGMENT/PLATFORM_FREEZE-only, or null (merges both repos, sorts by createdDate DESC, slices to Pageable)
- `acknowledge`/`resolve` call `AlertStatus.canTransitionTo()` before writing; throw `AlertStatusTransitionException` on invalid transitions; save one `DeviationAlertEvent` row with exactly one FK set
- 7 unit tests cover all 7 must-have behaviors from plan; total test suite 213, 0 failures

## Task Commits

Each task was committed atomically:

1. **Task 1: DeviationAlertManagementService** - `3ede5be` (feat)
2. **Task 2: DeviationAlertManagementService unit tests** - `aeeb074` (test)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `service/DeviationAlertManagementService.java` - List/fetch/acknowledge/resolve operations; private toDto helpers for SegmentDeviationAlert, BalanceDeviationAlert, DeviationAlertEvent; saveEvent helper with single-FK pattern
- `test/...service/DeviationAlertManagementServiceTest.java` - 7 tests: DEVMGMT-01a (BALANCE filter), DEVMGMT-01b (SEGMENT filter), DEVMGMT-01c (null merge+sort), DEVMGMT-02 (getAlert auditTrail), DEVMGMT-03a (acknowledge OPEN), DEVMGMT-03b (acknowledge RESOLVED throws), DEVMGMT-04 (resolve ACKNOWLEDGED)

## Decisions Made

- **guardTransition helper:** Rather than inline `if (!canTransitionTo(...)) throw` in each method, extracted to `guardTransition(current, target, id)`. Keeps acknowledge and resolve bodies symmetric and easier to read.
- **saveEvent centralises FK assignment:** `balanceAlertIdFk` and `segmentAlertIdFk` are explicit nullable parameters to `saveEvent()`; caller passes non-null only for the relevant FK. Enforces the single-FK contract at the call site.
- **Comparator.nullsLast(reverseOrder()) for merge sort:** Handles the theoretical null-createdDate edge case (can occur if Spring Auditing is not wired for the entity) without crashing. Defensive practice consistent with existing service patterns.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

The Maven wrapper (`./mvnw`) failed because `.mvn/wrapper/maven-wrapper.properties` does not exist in the repository. Used system `mvn` directly (already on PATH via SDKMAN). All compilation and test commands succeeded.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `DeviationAlertManagementService` is fully implemented and tested — Plan 04 (REST controller) can delegate all operations to this service
- Plan 04 needs to add `AuditEventType` entries (per plan note) — not done here per plan instructions
- All four method signatures are final: `listAlerts(type, alertStatus, pageable)`, `getAlert(type, id)`, `acknowledge(type, id, note)`, `resolve(type, id, note)`

---
*Phase: 24-deviation-alert-management*
*Completed: 2026-03-17*
