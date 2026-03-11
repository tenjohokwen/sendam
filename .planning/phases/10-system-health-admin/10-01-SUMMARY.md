---
phase: 10-system-health-admin
plan: 01
subsystem: api
tags: [resilience4j, circuit-breaker, health, admin, spring-data, native-query, projection]

# Dependency graph
requires:
  - phase: 09-spend-reporting-admin
    provides: Repository<Object, Long> aggregation pattern, native @Query projections, AdminSpendResource pattern
  - phase: 08-delivery-analytics-admin
    provides: gateway module structure (contract/repo/service/api), admin controller pattern
provides:
  - GET /api/admin/health/circuit-breaker — Nexah circuit breaker state + sliding-window metrics
  - GET /api/admin/health/webhook-stats — webhook_delivery aggregate counts by attempt_status
  - GET /api/admin/health/provider-stats — send_request_recipient aggregate counts + failure rate
  - HealthRepository, HealthService, AdminHealthResource, five response types
  - AppEndpoints.SECURED_MAPPINGS migrated to Map.ofEntries() with 10 entries
affects: [11-future-phases, any phase adding a new SECURED_MAPPINGS entry]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CircuitBreakerRegistry injection in service layer (not controller) for in-memory health reads"
    - "failureRatePct passes -1.0f through when sliding window not yet full (not converted to 0.0)"
    - "Map.ofEntries() mandatory for SECURED_MAPPINGS from phase 10 onward"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/health/contract/WebhookStatsRow.java
    - src/main/java/com/softropic/sendam/gateway/health/contract/ProviderStatsRow.java
    - src/main/java/com/softropic/sendam/gateway/health/contract/CircuitBreakerHealthResponse.java
    - src/main/java/com/softropic/sendam/gateway/health/contract/WebhookHealthResponse.java
    - src/main/java/com/softropic/sendam/gateway/health/contract/ProviderStatsResponse.java
    - src/main/java/com/softropic/sendam/gateway/health/repo/HealthRepository.java
    - src/main/java/com/softropic/sendam/gateway/health/service/HealthService.java
    - src/main/java/com/softropic/sendam/gateway/health/api/AdminHealthResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "failureRatePct passes -1.0f through (not converted to 0.0) when Resilience4j sliding window not yet full — -1.0 is meaningful to the caller as 'insufficient data'"
  - "Provider stats SQL uses send_status NOT IN ('ACCEPTED','CANCELLED') for total_submitted — excludes pre-Nexah rows only; FAIL_FINALIZED excluded because it is a parent send_request status, not a recipient status"
  - "All-time totals (no date filters) for HLTH-02 and HLTH-03 — requirement text does not specify filters; can add in a later phase"
  - "Map.ofEntries() for SECURED_MAPPINGS — mandatory from phase 10 onward; future phases must NOT revert to Map.of()"

patterns-established:
  - "Map.ofEntries() pattern: all future AppEndpoints.SECURED_MAPPINGS changes must use Map.ofEntries() (never Map.of())"
  - "CircuitBreaker in-memory reads: inject CircuitBreakerRegistry into service layer, use circuitBreakerRegistry.circuitBreaker('nexah') by exact name"

# Metrics
duration: 4min
completed: 2026-03-11
---

# Phase 10 Plan 01: System Health Admin Summary

**Three read-only admin health endpoints exposing Nexah circuit breaker state (in-memory), webhook delivery aggregates, and provider send aggregates — plus AppEndpoints migrated to Map.ofEntries() for 10 secured mappings**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-11T19:45:16Z
- **Completed:** 2026-03-11T19:49:12Z
- **Tasks:** 2 of 2
- **Files modified:** 9 (8 created, 1 modified)

## Accomplishments

- Full `gateway/health/` module built with `contract/`, `repo/`, `service/`, `api/` sub-packages following phases 8/9 pattern exactly
- Three admin endpoints: `/api/admin/health/circuit-breaker`, `/webhook-stats`, `/provider-stats` — all ADMIN-only (path-level via AppEndpoints + method-level via `@PreAuthorize`)
- `AppEndpoints.SECURED_MAPPINGS` migrated from `Map.of()` (9 entries) to `Map.ofEntries()` (10 entries) with `ADMIN_HEALTH` as the 10th entry, future-proofed for phase 11+

## Task Commits

Each task was committed atomically:

1. **Task 1: Data layer — projection interfaces and aggregation repository** - `f542cdc` (feat)
2. **Task 2: API layer — response records, service, controller, security registration** - `a6f0a5c` (feat)

**Plan metadata:** (committed with this SUMMARY + STATE.md update)

## Files Created/Modified

- `gateway/health/contract/WebhookStatsRow.java` — projection interface: getTotalAttempts, getFailureCount, getExhaustedCount
- `gateway/health/contract/ProviderStatsRow.java` — projection interface: getTotalSubmitted, getDrReceived, getFailedCount
- `gateway/health/contract/CircuitBreakerHealthResponse.java` — record: state, failure_rate_pct, failed_calls, successful_calls, not_permitted_calls
- `gateway/health/contract/WebhookHealthResponse.java` — record: total_attempts, failure_count, exhausted_count
- `gateway/health/contract/ProviderStatsResponse.java` — record: total_submitted, dr_received, failed_count, failure_rate_pct
- `gateway/health/repo/HealthRepository.java` — extends Repository<Object, Long>, two native @Query aggregate methods
- `gateway/health/service/HealthService.java` — injects HealthRepository + CircuitBreakerRegistry, three public methods
- `gateway/health/api/AdminHealthResource.java` — @RestController at /api/admin/health, three @GetMapping, @PreAuthorize("hasRole('ADMIN')")
- `security/config/AppEndpoints.java` — ADMIN_HEALTH constant added; SECURED_MAPPINGS rewritten as Map.ofEntries() with 10 entries

## Decisions Made

- **failureRatePct passes -1.0f through:** Resilience4j returns -1.0 when the sliding window has not collected enough calls (< slidingWindowSize=10). The response passes this through without conversion so callers know data is insufficient, not that the failure rate is zero.
- **Provider stats SQL: FAIL_FINALIZED excluded at recipient level:** `FAIL_FINALIZED` is only set on the parent `send_request` by `DrCallbackService.finalizeParentIfAllTerminal()`. Individual recipients remain at `FAILED` (bad DR) or `COMPLETED` (good DR). SQL uses `send_status NOT IN ('ACCEPTED','CANCELLED')` for total_submitted.
- **All-time totals for HLTH-02/HLTH-03:** No date filters — plan requirements do not mention time filters. Can be added in a future phase if needed.
- **Map.ofEntries() mandatory:** Phase 10 decision: SECURED_MAPPINGS must use Map.ofEntries() from this point forward. Future phases adding entries must not revert to Map.of().

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. All data sources (CircuitBreakerRegistry, webhook_delivery, send_request_recipient) are already in the running application.

## Next Phase Readiness

- HLTH-01, HLTH-02, HLTH-03 all satisfied
- Phase 10 complete — all three health endpoints are functional and ADMIN-secured
- AppEndpoints ready for phase 11 (use Map.ofEntries() with an 11th entry when needed)
- 156 tests pass, no regressions

---
*Phase: 10-system-health-admin*
*Completed: 2026-03-11*
