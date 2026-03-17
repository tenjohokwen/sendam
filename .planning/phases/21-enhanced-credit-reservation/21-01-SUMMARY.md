---
phase: 21-enhanced-credit-reservation
plan: 01
subsystem: database
tags: [flyway, jpa, postgresql, entity, schema, credit-reservation]

# Dependency graph
requires:
  - phase: 20-account-freeze-infrastructure
    provides: V12 migration pattern (DEFAULT FALSE for NOT NULL columns, singleton table)
  - phase: 19-platform-credit
    provides: CreditReservationService reservation flow that will be extended in Plan 02
provides:
  - V13 Flyway migration adding raw_expected_credits and expected_segments columns
  - SendRequest entity with rawExpectedCredits long field
  - SendRequestRecipient entity with expectedSegments int field
  - Schema foundation for Phase 22 deviation detection between buffered and actual credits
affects:
  - 21-02 (CreditReservationService must populate rawExpectedCredits and expectedSegments)
  - 22 (final booking reads both fields for per-recipient deviation detection)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DEFAULT 0 for NOT NULL BIGINT/INT columns in ALTER TABLE — mirrors V12 DEFAULT FALSE pattern"
    - "No @Builder.Default on long/int fields — Java primitive 0 default suffices; avoids Lombok builder complexity"

key-files:
  created:
    - src/main/resources/db/migration/V13__enhanced_reservation.sql
  modified:
    - src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequest.java
    - src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRecipient.java

key-decisions:
  - "No @Builder.Default on rawExpectedCredits/expectedSegments — 0 is Java's natural primitive default; omitting @Builder.Default keeps Lombok builder clean"
  - "raw_expected_credits placed between reservation_id and finalized_at in SendRequest — logical grouping with other credit reservation fields"
  - "expected_segments placed between segments_consumed and @Builder.Default status in SendRequestRecipient — natural grouping with parallel consumption field"

patterns-established:
  - "DEFAULT 0 pattern: ALTER TABLE with NOT NULL numeric columns uses DEFAULT 0 to satisfy constraint for existing rows"
  - "No-annotation primitive fields: new long/int JPA fields only need @Column; no @Builder.Default, no @Enumerated, no @JsonProperty"

# Metrics
duration: 4min
completed: 2026-03-17
---

# Phase 21 Plan 01: Enhanced Credit Reservation Schema Summary

**V13 Flyway migration adds raw_expected_credits (BIGINT) to send_request and expected_segments (INT) to send_request_recipient, with matching JPA entity fields, giving Phase 22 the per-recipient data needed for buffered-vs-actual deviation detection.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-17T13:39:18Z
- **Completed:** 2026-03-17T13:43:08Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- V13 migration created with both ALTER TABLE statements using DEFAULT 0 for backward-compatible NOT NULL columns
- SendRequest entity gains rawExpectedCredits (long, @Column raw_expected_credits) between reservationId and finalizedAt
- SendRequestRecipient entity gains expectedSegments (int, @Column expected_segments) between segmentsConsumed and @Builder.Default status
- All 190 existing tests pass with zero regressions — new fields are transparent to existing code paths

## Task Commits

Each task was committed atomically:

1. **Task 1: V13 Flyway migration** - `1b21612` (chore)
2. **Task 2: Entity field additions** - `461bd7f` (feat)

**Plan metadata:** _(pending docs commit)_

## Files Created/Modified

- `src/main/resources/db/migration/V13__enhanced_reservation.sql` - Two ALTER TABLE statements adding raw_expected_credits and expected_segments with DEFAULT 0
- `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequest.java` - Added rawExpectedCredits long field mapped to raw_expected_credits column
- `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRecipient.java` - Added expectedSegments int field mapped to expected_segments column

## Decisions Made

- No @Builder.Default on new long/int fields — Java primitive default of 0 is exactly what the schema DEFAULT 0 provides; adding @Builder.Default unnecessarily complicates the Lombok @SuperBuilder chain
- Field placement mirrors logical column grouping: rawExpectedCredits sits alongside reservedCredits (same reservation context); expectedSegments sits alongside segmentsConsumed (same per-recipient usage context)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - mvnw was missing .mvn/wrapper/maven-wrapper.properties so system Maven (mvn) was used instead; both commands are equivalent for this project.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Schema foundation is in place for Plan 02 to populate rawExpectedCredits and expectedSegments during credit reservation
- Both new columns default to 0; Phase 22 deviation detection assumes non-zero values written by Plan 02's updated CreditReservationService.reserve() logic
- No blockers for 21-02

---
*Phase: 21-enhanced-credit-reservation*
*Completed: 2026-03-17*
