---
phase: 22-final-booking-segment-deviation
plan: 01
subsystem: database
tags: [flyway, jpa, hibernate, jsonb, hypersistence-utils, enums, postgresql]

# Dependency graph
requires:
  - phase: 21-enhanced-credit-reservation
    provides: rawExpectedCredits/expectedSegments fields on SendRequest/SendRequestRecipient that Plan 02 compares against actual segments
  - phase: 19-platform-ledger
    provides: LedgerEntryType enum and AbstractAuditingEntity base class that SegmentDeviationAlert extends
  - phase: 20-account-freeze
    provides: AuditEventType enum extended here with SEGMENT_DEVIATION and PLATFORM_FREEZE_SHORTFALL
provides:
  - V14 Flyway migration creating main.segment_deviation_alert table with JSONB per_recipient_breakdown
  - SegmentDeviationAlert JPA entity with full column mapping and inner RecipientDeviationEntry record
  - SegmentDeviationAlertRepository Spring Data stub
  - DeviationAlertType enum (SEGMENT, PLATFORM_FREEZE) in billing.contract
  - LedgerEntryType.SMS_EXTRA_DEBIT for BOOK-04/05/06 extra client debit beyond buffered reservation
  - AuditEventType.SEGMENT_DEVIATION and AuditEventType.PLATFORM_FREEZE_SHORTFALL for audit trail
affects:
  - 22-02-PLAN.md: FinalBookingService and SegmentDeviationService require all types from this plan
  - 22-03-PLAN.md: SmsFinalisedBillingListener modification depends on FinalBookingService from Plan 02

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BIGINT PRIMARY KEY (TSID-generated) for new append-only audit tables — no BIGSERIAL/sequence"
    - "@Type(JsonType.class) + @Column(columnDefinition = jsonb) for structured JSONB list columns"
    - "Inner record pattern: RecipientDeviationEntry defined inside owning entity (not separate file)"
    - "@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE for non-singleton entities"

key-files:
  created:
    - src/main/resources/db/migration/V14__segment_deviation_alert.sql
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertType.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlert.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlertRepository.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/billing/contract/LedgerEntryType.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java

key-decisions:
  - "BIGINT PRIMARY KEY (not BIGSERIAL): V14 corrected from plan template to match all other project tables that use TSID-generated IDs"
  - "RecipientDeviationEntry as inner record inside SegmentDeviationAlert: keeps JSONB type co-located with entity; no separate file needed"
  - "SegmentDeviationAlertRepository is a minimal stub: Phase 24 adds query/filter methods; Plan 02 only needs save()"

patterns-established:
  - "JSONB column with hypersistence JsonType: @Type(JsonType.class) + @Column(columnDefinition = jsonb) + List<RecordType>"
  - "Deviation alert table: BIGINT PK, alert_type discriminator, financial_action outcome, nullable shortfall/unrecovered columns"

# Metrics
duration: 33min
completed: 2026-03-17
---

# Phase 22 Plan 01: Segment Deviation Persistence Layer Summary

**V14 migration + SegmentDeviationAlert entity/repository + 3 new enum values (SMS_EXTRA_DEBIT, SEGMENT_DEVIATION, PLATFORM_FREEZE_SHORTFALL) — full persistence foundation for Phase 22 booking and deviation detection**

## Performance

- **Duration:** ~33 min
- **Started:** 2026-03-17T14:00:00Z
- **Completed:** 2026-03-17T14:33:53Z
- **Tasks:** 2/2
- **Files modified:** 6

## Accomplishments

- V14__segment_deviation_alert.sql creates `main.segment_deviation_alert` with JSONB `per_recipient_breakdown` column and three indexes (client_id, send_request_id_fk, alert_type)
- SegmentDeviationAlert JPA entity maps all 15 columns including JSONB list via `@Type(JsonType.class)`, with inner `RecipientDeviationEntry` record for type-safe breakdown storage
- Three enum additions: `LedgerEntryType.SMS_EXTRA_DEBIT`, `AuditEventType.SEGMENT_DEVIATION`, `AuditEventType.PLATFORM_FREEZE_SHORTFALL` — unblocks Plan 02 service compilation
- All 194 existing tests pass with zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: V14 Flyway migration + enum additions** - `6821d18` (feat)
2. **Task 2: SegmentDeviationAlert entity + repository** - `01c0dbc` (feat)

**Plan metadata:** (committed after SUMMARY.md and STATE.md update)

## Files Created/Modified

- `src/main/resources/db/migration/V14__segment_deviation_alert.sql` - New Flyway migration; creates segment_deviation_alert table with JSONB column and 3 indexes
- `src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertType.java` - New enum: SEGMENT and PLATFORM_FREEZE values
- `src/main/java/com/softropic/sendam/gateway/billing/contract/LedgerEntryType.java` - Added SMS_EXTRA_DEBIT constant
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java` - Added SEGMENT_DEVIATION and PLATFORM_FREEZE_SHORTFALL constants
- `src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlert.java` - New JPA entity with all fields, @Type(JsonType.class) JSONB mapping, and inner RecipientDeviationEntry record
- `src/main/java/com/softropic/sendam/gateway/billing/repo/SegmentDeviationAlertRepository.java` - Spring Data JpaRepository stub

## Decisions Made

- **BIGINT PRIMARY KEY (not BIGSERIAL):** Plan template used `BIGSERIAL PRIMARY KEY` but all project tables use `BIGINT PRIMARY KEY` with TSID-generated IDs (io.hypersistence.utils `@Tsid`). Corrected automatically to maintain consistency.
- **RecipientDeviationEntry as inner record:** The per-recipient JSONB type is only used by `SegmentDeviationAlert`; co-locating it as a public inner record avoids a separate file and keeps the type definition adjacent to its usage.
- **Minimal repository stub:** `SegmentDeviationAlertRepository` only extends `JpaRepository<SegmentDeviationAlert, Long>` with no query methods. Plan 02 only needs `save()`; Phase 24 will add filtered list/search queries when the admin API for deviation alerts is built.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected BIGSERIAL to BIGINT PRIMARY KEY in V14 migration**
- **Found during:** Task 1 (V14 Flyway migration creation)
- **Issue:** Plan template specified `id BIGSERIAL PRIMARY KEY` for segment_deviation_alert table. All other tables in the project (client_credit_balance, platform_credit_balance, platform_freeze_state, audit_event, etc.) use `id BIGINT PRIMARY KEY` with TSID-generated values from `io.hypersistence.utils @Tsid`. Using BIGSERIAL would create a sequence that JPA would ignore (BaseEntity uses @Tsid, not @GeneratedValue), causing ID collisions between sequence and TSID values on insert.
- **Fix:** Changed to `BIGINT PRIMARY KEY` consistent with all existing tables. Added comment: "PK is BIGINT generated by TSID (io.hypersistence.utils) — no sequence DDL needed."
- **Files modified:** `src/main/resources/db/migration/V14__segment_deviation_alert.sql`
- **Verification:** `mvn compile` exits 0; pattern matches V10, V11, V12 migration files confirmed
- **Committed in:** `6821d18` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary correction for correctness — BIGSERIAL sequence would conflict with TSID ID generation strategy. No scope creep.

## Issues Encountered

None — both tasks compiled on first attempt after the BIGSERIAL correction.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 02 (`FinalBookingService` + `SegmentDeviationService`) can now import:
- `DeviationAlertType` (SEGMENT, PLATFORM_FREEZE)
- `LedgerEntryType.SMS_EXTRA_DEBIT`
- `AuditEventType.SEGMENT_DEVIATION`, `AuditEventType.PLATFORM_FREEZE_SHORTFALL`
- `SegmentDeviationAlert`, `SegmentDeviationAlert.RecipientDeviationEntry`
- `SegmentDeviationAlertRepository`

No blockers. 194 existing tests pass.

---
*Phase: 22-final-booking-segment-deviation*
*Completed: 2026-03-17*
