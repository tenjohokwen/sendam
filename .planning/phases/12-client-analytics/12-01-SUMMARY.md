---
phase: 12-client-analytics
plan: 01
subsystem: api
tags: [analytics, spring-security, rest, jackson, api-key-auth]

# Dependency graph
requires:
  - phase: 08-admin-analytics
    provides: DeliveryAnalyticsService, DeliveryAnalyticsRepository, SpendService, SpendRepository — aggregation infrastructure this plan extends
  - phase: 09-admin-spend
    provides: SpendSummaryRow, SpendRepository.findSpendSummary — reused for client credit consumption
  - phase: 06-fix-api-key-security-chain
    provides: ApiKeyAuthenticationFilter + ClientSecurityConfiguration @Order(1) chain — extended to claim /v1/analytics/**
provides:
  - ClientDeliveryStatsResponse DTO (total_sent, delivered, failed, delivery_rate, total_segments)
  - ClientSegmentTotalsResponse DTO (total_segments, from, to)
  - ClientCreditConsumptionResponse DTO (net_credits_consumed, from, to)
  - DeliveryAnalyticsService.getClientDeliveryStats + getClientSegmentTotals (client-scoped, no daily breakdown)
  - SpendService.getClientNetCreditsConsumed (net total only, no per-type breakdown)
  - ClientAnalyticsResource: GET /v1/analytics/delivery-stats, /segment-totals, /credits-consumed
  - AppEndpoints.CLIENT_ANALYTICS = "/v1/analytics/**"
  - ClientSecurityConfiguration securityMatcher now claims /v1/analytics/**
affects: [future v1.x analytics, any phase adding client-facing /v1/** endpoints]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - clientId extracted from SecurityContextHolder.getAuthentication().getPrincipal() as raw Long cast — mirrors SmsResource pattern
    - @DateTimeFormat(iso = DATE_TIME) on Instant @RequestParam for ISO-8601 filter params
    - Client response DTOs omit admin-only fields (no client_id echo, no daily_breakdown, no per-entry-type breakdown)
    - securityMatcher expanded by adding constant to existing varargs call — CLIENT_ANALYTICS not in SECURED_MAPPINGS (JWT chain would double-claim)

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/ClientDeliveryStatsResponse.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/ClientSegmentTotalsResponse.java
    - src/main/java/com/softropic/sendam/gateway/analytics/contract/ClientCreditConsumptionResponse.java
    - src/main/java/com/softropic/sendam/gateway/analytics/api/ClientAnalyticsResource.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/analytics/service/DeliveryAnalyticsService.java
    - src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java
    - src/main/java/com/softropic/sendam/gateway/auth/config/ClientSecurityConfiguration.java

key-decisions:
  - "CLIENT_ANALYTICS not added to SECURED_MAPPINGS — /v1/** catch-all already covers it in the JWT chain; adding it would double-claim; the constant is only for securityMatcher reference"
  - "No @PreAuthorize on ClientAnalyticsResource — security enforced by @Order(1) API-key filter chain, not role annotations"
  - "clientId logged at DEBUG only — PII guard; INFO logs must never contain client identity"
  - "getClientSegmentTotals reuses repository.findDeliveryStats — same aggregate row provides total_segments; no duplicate query needed"
  - "getClientNetCreditsConsumed reads only getNetCreditsConsumed() from SpendSummaryRow — per-type breakdown (getSmsDebit etc.) intentionally excluded per CANL-03 scope"

patterns-established:
  - "Client-facing analytics DTOs: no client_id echo, no admin-only breakdown fields"
  - "securityMatcher varargs extended by adding AppEndpoints constant — keeps all claimed paths visible in one call"

# Metrics
duration: 15min
completed: 2026-03-11
---

# Phase 12 Plan 01: Client Analytics Summary

**Three client-facing analytics REST endpoints (CANL-01/02/03) secured by API-key chain, with response DTOs that expose only client-scoped aggregates — no admin fields, no daily breakdown, no per-type spend breakdown**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-11T23:00:00Z
- **Completed:** 2026-03-11T23:15:00Z
- **Tasks:** 2
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments

- Three response record types with exact JSON field shapes specified by CANL-01/02/03 — no admin-only fields leaked
- Client delivery stats endpoint returns delivery_rate safely (0.0 guard when total_sent==0, no NaN/exception)
- API-key security chain (`@Order(1)`) now claims `/v1/analytics/**` so analytics requests are authenticated by Bearer API key, not JWT

## Task Commits

Each task was committed atomically:

1. **Task 1: Response DTOs and client-scoped service methods** - `5a13bf8` (feat)
2. **Task 2: ClientAnalyticsResource + AppEndpoints constant + securityMatcher registration** - `b936b6b` (feat)

## Files Created/Modified

- `gateway/analytics/contract/ClientDeliveryStatsResponse.java` - Record with total_sent, delivered, failed, delivery_rate, total_segments (@JsonProperty snake_case)
- `gateway/analytics/contract/ClientSegmentTotalsResponse.java` - Record with total_segments, from, to (no client_id)
- `gateway/analytics/contract/ClientCreditConsumptionResponse.java` - Record with net_credits_consumed, from, to (no per-type breakdown)
- `gateway/analytics/api/ClientAnalyticsResource.java` - Three GET handlers under /v1/analytics/**; clientId from SecurityContextHolder only
- `gateway/analytics/service/DeliveryAnalyticsService.java` - Added getClientDeliveryStats + getClientSegmentTotals (no findDailyBreakdown)
- `gateway/spend/service/SpendService.java` - Added getClientNetCreditsConsumed (reads getNetCreditsConsumed() only)
- `security/config/AppEndpoints.java` - Added CLIENT_ANALYTICS = "/v1/analytics/**" constant (not in SECURED_MAPPINGS)
- `gateway/auth/config/ClientSecurityConfiguration.java` - securityMatcher extended with CLIENT_ANALYTICS as 5th argument

## Decisions Made

- `CLIENT_ANALYTICS` constant not added to `SECURED_MAPPINGS` — the `/v1/**` catch-all in the JWT chain already covers it, and adding it would require that admin role access which is wrong for client endpoints. The constant exists only for `securityMatcher` reference.
- No `@PreAuthorize` on `ClientAnalyticsResource` — the `@Order(1)` API-key filter chain enforces authentication; role annotations would add nothing and diverge from the `SmsResource` pattern.
- `clientId` logged at DEBUG level only — PII guard; INFO/WARN logs must not expose client identity.
- `getClientSegmentTotals` reuses `repository.findDeliveryStats` — the aggregate row already contains `total_segments`; no separate query or new repository method needed.
- `getClientNetCreditsConsumed` reads `getNetCreditsConsumed()` only from `SpendSummaryRow` — per-type breakdown getters exist but are intentionally excluded per CANL-03 scope boundary.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

The `.mvn/wrapper/` directory was absent so `./mvnw compile` failed with a missing `maven-wrapper.properties` error. Fell back to system `mvn compile` which succeeded cleanly. This is a pre-existing environment issue, not introduced by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- CANL-01, CANL-02, CANL-03 fully implemented and compiled; v1.1 milestone complete
- Boot smoke test (401 without key, 200 with valid key) should be run against a running instance before final sign-off
- No blockers for future phases

---
*Phase: 12-client-analytics*
*Completed: 2026-03-11*
