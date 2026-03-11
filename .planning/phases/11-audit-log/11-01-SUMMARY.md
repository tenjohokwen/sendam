---
phase: 11-audit-log
plan: "01"
subsystem: database
tags: [flyway, jpa, postgresql, spring-data, tsid, projection-interface, audit]

# Dependency graph
requires:
  - phase: 01-auth
    provides: BaseEntity (TSID id, no extra columns)
  - phase: 05-webhooks
    provides: V7 migration conventions — BIGINT PK, schema = "main", TSID pattern
provides:
  - V10 Flyway migration creating main.audit_event table with two indexes
  - AuditEventType enum (11 constants covering AUDT-01 through AUDT-04)
  - DomainAuditEvent record (application event POJO for service-to-listener dispatch)
  - AuditEventRow projection interface (6 getters matching SQL column aliases)
  - AuditEventEntity extending BaseEntity, mapped to main.audit_event
  - AuditEventRepository JpaRepository with native paginated findEvents + explicit countQuery
affects:
  - 11-02 (write pipeline — AuditEventService + AuditEventListener + service hooks)
  - 11-03 (admin query API — AdminAuditResource + AuditEventResponse)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Append-only entity extends BaseEntity only (not AbstractAuditingEntity) — avoids unwanted status column"
    - "Native paginated JpaRepository query always supplies explicit countQuery attribute"
    - "Projection interface getter names match SQL column aliases via camelCase convention"

key-files:
  created:
    - src/main/resources/db/migration/V10__audit_event.sql
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/DomainAuditEvent.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventRow.java
    - src/main/java/com/softropic/sendam/gateway/audit/repo/AuditEventEntity.java
    - src/main/java/com/softropic/sendam/gateway/audit/repo/AuditEventRepository.java
  modified: []

key-decisions:
  - "AuditEventEntity extends BaseEntity (not AbstractAuditingEntity) — audit_event is append-only; AbstractAuditingEntity adds status + auditing columns that are unwanted here"
  - "client_id is nullable FK — purely admin events (e.g. CLIENT_CREATED which has a client target) are common, but future event types must not be constrained to require a client"
  - "DomainAuditEvent is a plain record (not a Spring ApplicationEvent subclass) — services publish it via ApplicationEventPublisher; listener receives it via @EventListener"
  - "findEvents uses nativeQuery=true with explicit countQuery — Spring cannot derive count from native SQL with complex WHERE; explicit countQuery is mandatory for Page<T>"

patterns-established:
  - "Append-only entity: extend BaseEntity only — no AbstractAuditingEntity when entity has no lifecycle status"
  - "Native paginated query: always provide countQuery alongside nativeQuery=true when returning Page<T>"

# Metrics
duration: 3min
completed: 2026-03-11
---

# Phase 11 Plan 01: Audit Log Data Layer Summary

**Flyway V10 migration + JPA audit_event table, 11-constant AuditEventType enum, DomainAuditEvent record, AuditEventRow projection, and JpaRepository with native paginated findEvents — foundation for AUDT-01 through AUDT-05**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-11T21:16:00Z
- **Completed:** 2026-03-11T21:18:57Z
- **Tasks:** 2/2
- **Files modified:** 6

## Accomplishments

- Created V10__audit_event.sql Flyway migration with audit_event table (6 columns: id, event_type, client_id, actor, detail, occurred_at) and two indexes (client+date partial, date full-scan)
- Created the full gateway/audit module — contract/ and repo/ sub-packages with all five Java types
- Confirmed AuditEventEntity extends BaseEntity (not AbstractAuditingEntity), keeping the table append-only with no unwanted status or auditing columns

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migration — audit_event table** - `18bad2b` (feat)
2. **Task 2: Contract types and data layer** - `3c23b22` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/db/migration/V10__audit_event.sql` — CREATE TABLE main.audit_event + two indexes
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java` — 11-constant enum for AUDT-01 through AUDT-04 event types
- `src/main/java/com/softropic/sendam/gateway/audit/contract/DomainAuditEvent.java` — record with eventType, clientId (nullable), actor, detail
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventRow.java` — projection interface with 6 getters matching SQL column aliases
- `src/main/java/com/softropic/sendam/gateway/audit/repo/AuditEventEntity.java` — @Entity extending BaseEntity, maps to main.audit_event
- `src/main/java/com/softropic/sendam/gateway/audit/repo/AuditEventRepository.java` — JpaRepository with native findEvents(clientId, from, to, Pageable) + explicit countQuery

## Decisions Made

- **BaseEntity only (not AbstractAuditingEntity):** audit_event is append-only. AbstractAuditingEntity carries a `status` column mapped as `@Column(name="status")` which would add an unwanted column to an immutable audit table. Extending BaseEntity gives only the TSID id field.
- **client_id nullable FK:** Admin-only events (CLIENT_CREATED, TOPUP_APPROVED, etc.) still have a client target, but future event types must not be forced to provide one. The FK references main.client_account(id) with no ON DELETE CASCADE — orphan rows are acceptable for an audit trail.
- **DomainAuditEvent as plain record:** No need to extend Spring ApplicationEvent. Publishing via ApplicationEventPublisher works with any object; keeping it a simple record avoids unnecessary framework coupling.
- **Explicit countQuery mandatory:** Spring Data cannot derive count from native SQL with conditional WHERE clauses. Omitting countQuery on a nativeQuery=true Page<T> method throws at runtime.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 02 (write pipeline) can proceed immediately:
- AuditEventType, DomainAuditEvent, AuditEventEntity, and AuditEventRepository are all available
- AuditEventService (REQUIRES_NEW isolation) and AuditEventListener (@EventListener) need to be built
- Five service classes need ApplicationEventPublisher injection + publishEvent calls at the hook points documented in 11-RESEARCH.md

No blockers.

---
*Phase: 11-audit-log*
*Completed: 2026-03-11*
