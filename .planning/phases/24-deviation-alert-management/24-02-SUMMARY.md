---
phase: 24-deviation-alert-management
plan: 02
subsystem: api
tags: [spring-boot, jpa, jpql, dto, exception, validation, billing, deviation-alerts]

# Dependency graph
requires:
  - phase: 24-01
    provides: AlertStatus enum, DeviationAlertEvent entity + repo, SegmentDeviationAlert/BalanceDeviationAlert entities with alertStatus field
  - phase: 22-01
    provides: SegmentDeviationAlert entity and minimal save()-only repo stub
  - phase: 23-01
    provides: BalanceDeviationAlert entity and minimal save()-only repo stub
provides:
  - DeviationAlertError enum (ALERT_NOT_FOUND, INVALID_STATUS_TRANSITION)
  - AlertStatusTransitionException domain exception with alertId + currentStatus in logContext
  - DeviationAlertNoteRequest validated request record
  - DeviationAlertEventDto audit trail row DTO
  - DeviationAlertDto union response record covering all three alert types with inner RecipientBreakdownDto
  - SegmentDeviationAlertRepository.findByOptionalFilters JPQL paginated query (type + alertStatus filters)
  - BalanceDeviationAlertRepository.findByOptionalFilters JPQL paginated query (alertStatus filter)
affects: [24-03, 24-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Nullable-enum JPQL pattern: (:param IS NULL OR e.field = :param) — handles optional enum filters without native SQL CAST workarounds"
    - "Union DTO pattern: single DeviationAlertDto record covers all three alert types with nullable type-specific fields"
    - "Inner record pattern: RecipientBreakdownDto co-located inside DeviationAlertDto — co-location with owning DTO"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertError.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/AlertStatusTransitionException.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertNoteRequest.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertEventDto.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertDto.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlertRepository.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlertRepository.java

key-decisions:
  - "BalanceDeviationAlertRepository.findByOptionalFilters omits the type param — BALANCE table only contains type=BALANCE; service layer (Plan 03) handles routing; never passes type filter to the balance repo"
  - "DeviationAlertDto uses Long (boxed) for numeric type-specific fields (nexahBalance, sendamBalance, shortfallAmount, etc.) — null signals field is not applicable for that alert type; primitive long only for fields guaranteed on all types (delta)"
  - "RecipientBreakdownDto is an inner record inside DeviationAlertDto — co-located with owning DTO; no separate file needed"

patterns-established:
  - "Nullable-enum JPQL filter: both repos use (:param IS NULL OR a.field = :param) in JPQL text blocks — Plan 03/04 should pass null to omit the filter"
  - "Union DTO with nullable fields: plan callers must check alert type before reading type-specific fields; auditTrail is null on list responses, populated on detail fetch"

# Metrics
duration: 4min
completed: 2026-03-17
---

# Phase 24 Plan 02: Contract Types and Repository Query Methods Summary

**DeviationAlertError enum, AlertStatusTransitionException, three request/response DTOs, and JPQL findByOptionalFilters on both alert repositories — complete vocabulary for DeviationAlertManagementService (Plan 03)**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-17T16:28:13Z
- **Completed:** 2026-03-17T16:33:04Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Created 5 contract files: error code enum, domain exception, validated request record, audit event DTO, and union response DTO covering all three alert types
- Extended both repository stubs (save()-only since Phase 22/23) with paginated JPQL `findByOptionalFilters` methods using nullable-enum pattern
- All 206 existing tests pass with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Exception type, error code enum, and request/response contract records** - `ce81022` (feat)
2. **Task 2: JPQL query methods on both repository stubs** - `12cd6ba` (feat)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `contract/DeviationAlertError.java` - Error code enum implementing ErrorCode with ALERT_NOT_FOUND and INVALID_STATUS_TRANSITION
- `contract/AlertStatusTransitionException.java` - Domain exception extending ApplicationException; logContext populated with alertId + currentStatus
- `contract/DeviationAlertNoteRequest.java` - Validated request record with @NotBlank @Size(max=500) on note
- `contract/DeviationAlertEventDto.java` - Audit trail row DTO (id, previousStatus, newStatus, adminNote, actedBy, actedAt)
- `contract/DeviationAlertDto.java` - Union response record for SEGMENT/PLATFORM_FREEZE/BALANCE with inner RecipientBreakdownDto
- `repo/SegmentDeviationAlertRepository.java` - Extended with findByOptionalFilters(type, alertStatus, pageable)
- `repo/BalanceDeviationAlertRepository.java` - Extended with findByOptionalFilters(alertStatus, pageable)

## Decisions Made

- **BalanceDeviationAlertRepository omits type param:** BALANCE table only ever contains type=BALANCE; the service layer (Plan 03) is responsible for routing (if caller requests type=BALANCE, service calls balance repo; it never passes a type filter to the balance repo itself). Keeps method signature minimal and avoids a no-op filter.
- **DeviationAlertDto uses Long (boxed) for type-specific numerics:** Fields like nexahBalance, sendamBalance, shortfallAmount, and unrecoveredAmount are null for inapplicable alert types. Using boxed Long signals "not applicable" vs primitive long which can't carry null. Only `delta` is primitive because all three alert types carry it.
- **RecipientBreakdownDto as inner record of DeviationAlertDto:** Co-location with the owning DTO prevents the breakdown type from leaking into the package-level namespace. Only callers of DeviationAlertDto need to reference RecipientBreakdownDto — referenced as `DeviationAlertDto.RecipientBreakdownDto`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - `mvn compile` succeeded on all files; 206 tests passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All contract vocabulary is in place for DeviationAlertManagementService (Plan 03): exception types, DTOs, and repository query methods
- Plan 03 can route by type (SEGMENT/PLATFORM_FREEZE → segment repo, BALANCE → balance repo) and map entities to DeviationAlertDto
- Both findByOptionalFilters methods accept null params to omit filters — Plan 03 passes null when no filter is requested

---
*Phase: 24-deviation-alert-management*
*Completed: 2026-03-17*
