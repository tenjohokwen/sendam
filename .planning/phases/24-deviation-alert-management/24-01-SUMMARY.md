---
phase: 24-deviation-alert-management
plan: 01
subsystem: database
tags: [postgres, flyway, jpa, hibernate, lombok, tsid]

# Dependency graph
requires:
  - phase: 22-segment-deviation-alert
    provides: SegmentDeviationAlert entity and segment_deviation_alert table
  - phase: 23-periodic-balance-reconciliation
    provides: BalanceDeviationAlert entity and balance_deviation_alert table
provides:
  - V16 migration adding alert_status column to both alert tables
  - deviation_alert_event audit trail table (TSID PK, nullable FK cols, acted_at/acted_by)
  - AlertStatus enum with canTransitionTo() transition guard
  - DeviationAlertEvent JPA entity extending BaseEntity
  - DeviationAlertEventRepository with finders by segment and balance FK
  - alertStatus field on SegmentDeviationAlert and BalanceDeviationAlert (defaulting to OPEN)
affects:
  - 24-02 (DeviationAlertService uses AlertStatus, DeviationAlertEvent, and both repos)
  - 24-03 (Admin API reads alertStatus for filtering/display)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BaseEntity extension for event/audit entities that own acted_at/acted_by (not AbstractAuditingEntity)"
    - "Nullable FK pattern: exactly one of segmentAlertIdFk/balanceAlertIdFk non-null per event row"
    - "canTransitionTo() switch expression in enum for compile-time-exhaustive transition guards"

key-files:
  created:
    - src/main/resources/db/migration/V16__deviation_alert_lifecycle.sql
    - src/main/java/com/softropic/sendam/gateway/billing/contract/AlertStatus.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/DeviationAlertEvent.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/DeviationAlertEventRepository.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlert.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlert.java

key-decisions:
  - "DeviationAlertEvent extends BaseEntity (not AbstractAuditingEntity) — owns acted_at/acted_by; Spring Security auditing columns not applicable to admin-action events"
  - "Nullable FK pattern: segmentAlertIdFk null for BALANCE rows; balanceAlertIdFk null for SEGMENT/PLATFORM_FREEZE rows — single table for all alert event types"
  - "AlertStatus.canTransitionTo() uses Java 17 switch expression — exhaustive pattern means adding new enum constant forces compiler error until transition rules updated"

patterns-established:
  - "BaseEntity for event/audit tables: when entity owns its own temporal fields (acted_at/acted_by) instead of using Spring Security auditing, extend BaseEntity not AbstractAuditingEntity"
  - "Enum transition guard: canTransitionTo(Target) switch expression — exhaustive, no default case, compiler catches missing transition rules on new constants"

# Metrics
duration: 4min
completed: 2026-03-17
---

# Phase 24 Plan 01: Deviation Alert Lifecycle Foundation Summary

**V16 Flyway migration adds alert_status column to both alert tables and creates deviation_alert_event audit trail; AlertStatus enum with canTransitionTo() switch guard; DeviationAlertEvent entity extending BaseEntity with nullable segment/balance FKs**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-17T16:22:00Z
- **Completed:** 2026-03-17T16:26:09Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- V16 migration extends both existing alert tables with alert_status (default OPEN) and creates the deviation_alert_event audit trail table with all required columns and indexes
- AlertStatus enum (OPEN, ACKNOWLEDGED, RESOLVED) with canTransitionTo() Java 17 switch expression providing compile-time exhaustive transition validation
- DeviationAlertEvent JPA entity extending BaseEntity — purposefully avoids AbstractAuditingEntity because admin-action events own their own acted_at/acted_by fields
- DeviationAlertEventRepository with finders ordered by actedAt for chronological audit history

## Task Commits

Each task was committed atomically:

1. **Task 1: V16 migration — alert_status column on both tables + deviation_alert_event table** — `31f4aab` (chore)
2. **Task 2: AlertStatus enum + updated entities + DeviationAlertEvent entity + repo** — `0197c8b` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V16__deviation_alert_lifecycle.sql` — ALTER TABLE for both alert tables + CREATE TABLE deviation_alert_event + 4 indexes
- `src/main/java/com/softropic/sendam/gateway/billing/contract/AlertStatus.java` — OPEN/ACKNOWLEDGED/RESOLVED enum with canTransitionTo() guard
- `src/main/java/com/softropic/sendam/gateway/billing/repo/DeviationAlertEvent.java` — audit trail entity extending BaseEntity with nullable segment/balance FKs and acted_at/acted_by
- `src/main/java/com/softropic/sendam/gateway/billing/repo/DeviationAlertEventRepository.java` — Spring Data repo with findBySegmentAlertIdFkOrderByActedAtAsc and findByBalanceAlertIdFkOrderByActedAtAsc
- `src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlert.java` — added alertStatus field with @Builder.Default = OPEN
- `src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlert.java` — added alertStatus field with @Builder.Default = OPEN

## Decisions Made

- **DeviationAlertEvent extends BaseEntity (not AbstractAuditingEntity):** The event table owns its own acted_at/acted_by fields — these are the admin who performed the action, not Spring Security's current user at entity-save time. Extending AbstractAuditingEntity would add redundant created_by/last_modified_by columns.
- **Nullable FK pattern in deviation_alert_event:** A single event table covers all three alert types (SEGMENT, PLATFORM_FREEZE, BALANCE). Exactly one FK column is non-null per row: segmentAlertIdFk for segment/platform-freeze events, balanceAlertIdFk for balance events. This avoids a separate event table per alert type.
- **canTransitionTo() with Java 17 switch expression:** No default case — if a new AlertStatus constant is added in the future, the compiler forces the developer to update transition rules immediately rather than silently inheriting a default.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. The mvnw wrapper was missing, but the project uses the system Maven installation (mvn on PATH via SDKMAN), which worked identically.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All compile-time dependencies for Phase 24 Plan 02 are in place: AlertStatus, DeviationAlertEvent, DeviationAlertEventRepository
- Both alert entity classes now carry alertStatus, enabling DeviationAlertService (Plan 02) to perform status transitions without further schema changes
- 206 existing tests pass — no regressions

---
*Phase: 24-deviation-alert-management*
*Completed: 2026-03-17*
