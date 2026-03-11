---
phase: 08-delivery-analytics-admin
plan: 01
subsystem: api
tags: [spring-data-jpa, native-query, projection, analytics, admin, flyway]

# Dependency graph
requires:
  - phase: 05-send-request
    provides: send_request_recipient table with send_status, segments_consumed, client_id, created_date columns
provides:
  - GET /api/admin/analytics/delivery-stats — summary + daily breakdown (DANL-01, DANL-02, DANL-03)
  - GET /api/admin/analytics/segment-totals — segment totals with filter echo (DANL-02)
  - V8 Flyway migration — composite index on send_request_recipient(client_id, created_date)
  - gateway/analytics module — self-contained analytics package (contract, repo, service, api)
affects:
  - future admin dashboard phases that need delivery metrics
  - any client-facing analytics endpoint that reuses the repository pattern

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Spring Data Repository<Object, Long> (minimal marker) for pure-query repositories with no entity binding
    - Closed-projection interfaces (DeliveryStatRow, DeliveryDailyStatRow) for native @Query aggregation
    - java.sql.Date -> LocalDate conversion in service layer for DATE_TRUNC projection results
    - getSegmentTotals reuses findDeliveryStats (no second query method needed — same aggregate row has all fields)

key-files:
  created:
    - src/main/resources/db/migration/V8__analytics_index.sql
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryStatRow.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryDailyStatRow.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryDailyStat.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryStatsResponse.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/SegmentTotalsResponse.java
    - src/main/java/com/softropic/sendam/gateway/analytics/repo/DeliveryAnalyticsRepository.java
    - src/main/java/com/softropic/sendam/gateway/analytics/service/DeliveryAnalyticsService.java
    - src/main/java/com/softropic/sendam/gateway/analytics/api/AdminDeliveryAnalyticsResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "Repository<Object, Long> (not JpaRepository) — analytics queries are pure aggregation, no entity needed"
  - "getSegmentTotals reuses findDeliveryStats — avoids duplicate query method, same aggregate row covers both endpoints"
  - "java.sql.Date return type on DeliveryDailyStatRow.getDay() — Hibernate maps DATE_TRUNC::date to java.sql.Date cleanly; convert to LocalDate.toString() in service layer"
  - "delivery_rate = 0.0 when total_sent = 0 — explicit guard prevents division-by-zero"

patterns-established:
  - "Pure aggregation repo: extend Repository<Object, Long> with @Repository + native @Query methods — no entity binding required"
  - "Closed projection for native SQL: interface getter names must exactly match SQL column aliases"
  - "Admin endpoint pattern: @RestController + @RequestMapping + @PreAuthorize(hasRole(ADMIN)) + AppEndpoints constant + SECURED_MAPPINGS entry"

# Metrics
duration: 5min
completed: 2026-03-11
---

# Phase 8 Plan 01: Delivery Analytics Admin Summary

**Admin delivery analytics REST API using Spring Data native @Query projections — two endpoints returning per-client SMS delivery counts, delivery rate, segment totals, and day-level breakdown**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-11T18:33:30Z
- **Completed:** 2026-03-11T18:38:19Z
- **Tasks:** 2
- **Files modified:** 10 (9 created, 1 modified)

## Accomplishments

- Delivery analytics module (gateway/analytics) built from scratch with data, service, and API layers
- Two admin-only endpoints satisfy DANL-01, DANL-02, and DANL-03 in a single delivery-stats response plus a separate segment-totals endpoint
- Composite Flyway index on send_request_recipient(client_id, created_date) supports time-range queries; ADMIN_ANALYTICS security registration added to AppEndpoints

## Task Commits

Each task was committed atomically:

1. **Task 1: Data layer — Flyway index, projection interfaces, aggregation repository** - `68488a5` (feat)
2. **Task 2: API layer — response records, service, controller, security registration** - `431cbfe` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/db/migration/V8__analytics_index.sql` — composite index idx_srr_client_date on send_request_recipient(client_id, created_date)
- `src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryStatRow.java` — Spring Data closed-projection interface for summary aggregate (total_sent, delivered, failed, total_segments)
- `src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryDailyStatRow.java` — Spring Data closed-projection interface for daily breakdown (day as java.sql.Date, plus counters)
- `src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryDailyStat.java` — JSON response record for one day entry
- `src/main/java/com/softropic/sendam/gateway/analytics/contract/DeliveryStatsResponse.java` — JSON response record combining DANL-01/02/03 fields
- `src/main/java/com/softropic/sendam/gateway/analytics/contract/SegmentTotalsResponse.java` — JSON response record for segment-totals endpoint (echoes filter params)
- `src/main/java/com/softropic/sendam/gateway/analytics/repo/DeliveryAnalyticsRepository.java` — JPA repository extending Repository<Object, Long> with findDeliveryStats and findDailyBreakdown native @Query methods
- `src/main/java/com/softropic/sendam/gateway/analytics/service/DeliveryAnalyticsService.java` — maps projections to response records, computes delivery rate, converts java.sql.Date to LocalDate string
- `src/main/java/com/softropic/sendam/gateway/analytics/api/AdminDeliveryAnalyticsResource.java` — admin REST controller at /api/admin/analytics with two @GetMapping endpoints
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` — added ADMIN_ANALYTICS = "/api/admin/analytics/**" constant and 8th SECURED_MAPPINGS entry

## Decisions Made

- **Repository<Object, Long> not JpaRepository** — analytics queries are pure aggregation; no entity binding is needed. Avoids requiring a dummy entity and keeps intent clear.
- **getSegmentTotals reuses findDeliveryStats** — the same aggregate row already contains total_segments. A second query method would be redundant.
- **java.sql.Date for DeliveryDailyStatRow.getDay()** — Hibernate maps DATE_TRUNC('day', ...)::date to java.sql.Date in closed projections. Conversion to LocalDate.toString() done in service layer.
- **delivery_rate guard at total_sent == 0** — prevents ArithmeticException for empty windows; returns 0.0 as specified.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None — compilation succeeded on first attempt. Test suite (mvn test) passed with BUILD SUCCESS.

## User Setup Required

None — no external service configuration required. Flyway V8 migration is applied automatically at application startup.

## Next Phase Readiness

- Full delivery analytics API is deployed and secured for admin JWT holders
- DANL-01, DANL-02, DANL-03 requirements satisfied
- Repository pattern (Repository<Object, Long> + closed projection) is established and can be reused for future analytics queries
- No blockers for subsequent phases

---
*Phase: 08-delivery-analytics-admin*
*Completed: 2026-03-11*
