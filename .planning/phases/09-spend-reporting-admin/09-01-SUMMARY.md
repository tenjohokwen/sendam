---
phase: 09-spend-reporting-admin
plan: 01
subsystem: api
tags: [spring-data-jpa, native-query, projection-interface, postgresql, admin-api, spend-reporting]

# Dependency graph
requires:
  - phase: 03-credit-ledger
    provides: credit_ledger_entry table with entry_type, amount (signed), created_date
  - phase: 04-topup-billing
    provides: topup_request table with topup_status, approved_at, rejected_at, created_date
  - phase: 08-delivery-analytics-admin
    provides: Repository<Object, Long> pure-aggregation pattern, native @Query projection pattern

provides:
  - GET /api/admin/spend/credits — SpendSummaryResponse (net_credits_consumed + per-type breakdown)
  - GET /api/admin/spend/topups — TopupHistoryResponse (list of top-up records with optional status filter)
  - V9__spend_index.sql — composite index on topup_request(client_id, created_date DESC)
  - gateway/spend module: contract/, repo/, service/, api/ layers
  - AppEndpoints.ADMIN_SPEND constant registered in SECURED_MAPPINGS

affects: [future reporting phases, any phase reading spend/topup data for billing audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Repository<Object, Long> for pure-aggregation native queries (established Phase 8, confirmed Phase 9)
    - Spring Data closed-projection interfaces with camelCase getter-to-snake_case-alias mapping
    - java.sql.Timestamp in projections for TIMESTAMP columns — converted to Instant in service layer
    - String topupStatus param (not enum) in native query to avoid Hibernate enum-binding issues
    - Map.of() for SECURED_MAPPINGS — now at 9/10 pairs; next admin endpoint must switch to Map.ofEntries()

key-files:
  created:
    - src/main/resources/db/migration/V9__spend_index.sql
    - src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryRow.java
    - src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryRow.java
    - src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryResponse.java
    - src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryItem.java
    - src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryResponse.java
    - src/main/java/com/softropic/sendam/gateway/spend/repo/SpendRepository.java
    - src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java
    - src/main/java/com/softropic/sendam/gateway/spend/api/AdminSpendResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "SMS_DEBIT and SMS_RESERVATION amounts stored as negative in DB — ABS() applied in SQL so all breakdown fields are positive absolute values in the response"
  - "TOPUP_PENDING excluded from credit ledger spend summary (it always carries amount=0); included in topup history when present in topup_request table"
  - "String topupStatus param (not enum) in SpendRepository native query — avoids Hibernate enum-binding issues with nativeQuery=true"
  - "TopupHistoryRow.getApprovedAt() and getRejectedAt() return java.sql.Timestamp (nullable) — converted to Instant in service with null guard"
  - "AppEndpoints.SECURED_MAPPINGS now at 9/10 Map.of() pairs — next admin endpoint must switch to Map.ofEntries()"

patterns-established:
  - "Pattern: Pure-aggregation repository extends Repository<Object, Long> (not JpaRepository) — confirmed for second time in Phase 9"
  - "Pattern: Projection getters use java.sql.Timestamp for TIMESTAMP DB columns in native queries"
  - "Pattern: admin spend module self-contained in gateway/spend with contract/, repo/, service/, api/ layers mirroring gateway/analytics"

# Metrics
duration: 6min
completed: 2026-03-11
---

# Phase 9 Plan 01: Spend Reporting Admin Summary

**Two admin endpoints over native SQL aggregations: net-credits-consumed breakdown (SPEN-01/02) and filterable top-up history (SPEN-03) across a new self-contained gateway/spend module**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-11T19:00:58Z
- **Completed:** 2026-03-11T19:07:29Z
- **Tasks:** 2
- **Files modified:** 10 (9 created, 1 modified)

## Accomplishments

- Built complete gateway/spend module (contract, repo, service, api layers) following Phase 8 analytics pattern exactly
- Native SQL aggregation in SpendRepository returns SpendSummaryRow (5 credit-type fields + net_credits_consumed) and List<TopupHistoryRow> (10 fields per topup record)
- Both endpoints secured at two layers: AppEndpoints.SECURED_MAPPINGS path-level + @PreAuthorize("hasRole('ADMIN')") method-level
- V9 Flyway migration adds composite index on topup_request(client_id, created_date DESC) for date-range query performance
- All 156 existing tests pass — no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Data layer — Flyway index, projection interfaces, aggregation repository** - `6791701` (feat)
2. **Task 2: API layer — response records, service, controller, security registration** - `b43bd9f` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V9__spend_index.sql` - Composite index on topup_request(client_id, created_date DESC)
- `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryRow.java` - Projection interface for ledger aggregate query (5 getters)
- `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryRow.java` - Projection interface for topup history query (10 getters, Timestamp for nullable timestamps)
- `src/main/java/com/softropic/sendam/gateway/spend/repo/SpendRepository.java` - Repository<Object, Long> with two native @Query methods
- `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryResponse.java` - Response record for /credits endpoint (5 @JsonProperty fields)
- `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryItem.java` - Single topup DTO record (10 @JsonProperty fields, nullable Instants)
- `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryResponse.java` - Wrapper record with topups list
- `src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java` - @Transactional(readOnly = true) service mapping projections to response records
- `src/main/java/com/softropic/sendam/gateway/spend/api/AdminSpendResource.java` - @RestController at /api/admin/spend with /credits and /topups GET endpoints
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - Added ADMIN_SPEND constant + SECURED_MAPPINGS entry (now 9/10 Map.of() pairs)

## Decisions Made

- **Sign convention:** SMS_DEBIT and SMS_RESERVATION are stored as negative longs in the DB. ABS() applied in SQL so all response fields (`sms_debit`, `sms_reservation`) are positive absolute values — matches the spec requirement "positive absolute values".
- **TOPUP_PENDING exclusion:** Excluded from the credit ledger aggregate query only (it always has amount=0 and adds no information). It remains a valid `topup_status` in the topup history query.
- **String topupStatus in native query:** Passing enum name as String avoids Hibernate's binding of Java enum types in `nativeQuery=true` context. Controller accepts String directly and passes through unchanged.
- **Nullable Timestamps in TopupHistoryRow:** `getApprovedAt()` and `getRejectedAt()` return `java.sql.Timestamp` (nullable). Service applies explicit null guard before calling `.toInstant()`.
- **Map.of() entry count warning:** SECURED_MAPPINGS is now at 9 of 10 supported pairs. Documented as a concern — the next admin endpoint added to AppEndpoints must switch the static initializer to `Map.ofEntries(...)`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- The `mvnw` wrapper script failed because `.mvn/wrapper/maven-wrapper.properties` is absent from the repo. Used system `mvn` directly (`mvn compile -q -f pom.xml`). This is a pre-existing project condition, not introduced by this plan.

## User Setup Required

None - no external service configuration required. Flyway V9 migration applies automatically at startup.

## Next Phase Readiness

- Phase 9 (Spend Reporting Admin) complete. SPEN-01, SPEN-02, SPEN-03 requirements satisfied.
- Phase 10 (next phase) can proceed immediately.
- **Warning for future admin endpoints:** AppEndpoints.SECURED_MAPPINGS is at 9/10 Map.of() pairs. The next entry requires switching to `Map.ofEntries(Map.entry(...), ...)`.

---
*Phase: 09-spend-reporting-admin*
*Completed: 2026-03-11*
