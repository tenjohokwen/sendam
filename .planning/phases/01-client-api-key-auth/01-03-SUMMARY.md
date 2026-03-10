---
phase: 01-client-api-key-auth
plan: "03"
subsystem: auth
tags: [spring-security, rate-limiting, bucket4j, x-request-id, api-key, rest, filter-chain, aop]

# Dependency graph
requires:
  - phase: 01-02
    provides: ApiKeyService.generateAndPersist and .authenticate, ApiKeyAuthenticationFilter, @Order(1) ClientSecurityConfiguration scoped to /v1/api/**, ROLE_API_CLIENT principal

provides:
  - ClientApiKeyResource: POST/GET/DELETE /v1/api/keys (APIKEY-01, APIKEY-02, APIKEY-03)
  - ApiKeyService.createKey / .listKeys / .revokeKey
  - ApiKeyDto record (id, label, status, createdDate — no raw key value)
  - CreateKeyRequest record for POST body
  - RateLimitingService: Refill.greedy for sub-60s windows, N-token tryConsume overload for Phase 3
  - RateLimitingAspect: client_id identifier for ROLE_API_CLIENT requests (AUTH-05 10 req/s)
  - RequestIdResponseFilter: X-Request-ID header on all responses (AUTH-03)

affects:
  - Phase 3 (SMS send endpoint — wires N-token tryConsume overload for 1000 recipients/min AUTH-05 second half)
  - All subsequent phases relying on X-Request-ID header presence for request tracing

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RequestIdResponseFilter NOT @Component — registered via FilterRegistrationBean in SecurityConfiguration at HIGHEST_PRECEDENCE + 1 for global coverage across all chains"
    - "RateLimitingAspect checks ROLE_API_CLIENT authority to switch from IP to client_id as rate limit bucket key"
    - "Refill.greedy for sub-60s windows vs Refill.intervally for 60s+ — dispatch in createBucket avoids separate bucket types"
    - "revokeKey throws ResourceNotFoundException with identical message for missing vs cross-client key — obscures ownership"

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/api/ClientApiKeyResource.java
    - src/main/java/com/softropic/sendam/client/contract/ApiKeyDto.java
    - src/main/java/com/softropic/sendam/client/contract/CreateKeyRequest.java
    - src/main/java/com/softropic/sendam/security/infrastructure/filter/RequestIdResponseFilter.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/ApiKeyService.java
    - src/main/java/com/softropic/sendam/security/service/RateLimitingService.java
    - src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java
    - src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java

key-decisions:
  - "RequestIdResponseFilter registered via FilterRegistrationBean (not @Component) — same pattern as ApiKeyAuthenticationFilter in Plan 02; prevents double-registration outside intended scope"
  - "Refill.greedy for window < 60s — intervally would allow burst at second boundary (all 10 tokens after 1s wait), defeating per-second rate limit intent"
  - "revokeKey cross-client ownership hidden with identical error message — prevents client enumeration of other clients' key IDs"
  - "N-token tryConsume overload added to RateLimitingService in Phase 1 as infrastructure — enforcement wiring deferred to Phase 3 SMS send endpoint where recipient count is available"

patterns-established:
  - "Pattern: Global response headers use FilterRegistrationBean at HIGHEST_PRECEDENCE + N — not added to security chain filters"
  - "Pattern: Rate limit identifier switches on ROLE_API_CLIENT authority — clean separation between portal (IP-based) and API (client_id-based) rate limits"

# Metrics
duration: 6min
completed: 2026-03-10
---

# Phase 1 Plan 03: API Key Self-Service, Rate Limiting, and X-Request-ID Summary

**POST/GET/DELETE /v1/api/keys self-service endpoints, per-client Bucket4j greedy rate limiting at 10 req/s, and X-Request-ID response header registered globally via FilterRegistrationBean**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-10T13:45:00Z
- **Completed:** 2026-03-10T13:51:00Z
- **Tasks:** 2
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments
- `ClientApiKeyResource` delivers full API key self-service (APIKEY-01, APIKEY-02, APIKEY-03): POST returns new key with raw value once, GET lists keys without raw values, DELETE revokes immediately
- `ApiKeyService.revokeKey` uses identical `ResourceNotFoundException` messages for missing and cross-client keys — cross-client revocation attempt is indistinguishable from not-found
- `RateLimitingAspect.getClientIdentifier` now routes API-key-authenticated requests (ROLE_API_CLIENT) to `client:<clientId>` bucket, while portal/JWT requests keep IP-based bucketing
- `RateLimitingService.createBucket` dispatches to `Refill.greedy` for sub-60s windows — prevents burst-at-boundary problem for 1-second API rate limit (AUTH-05)
- `RequestIdResponseFilter` writes `X-Request-ID` from MDC to every HTTP response, registered at `HIGHEST_PRECEDENCE + 1` via `FilterRegistrationBean` for global coverage (AUTH-03)
- N-token `tryConsume` overload added to `RateLimitingService` as Phase 3 infrastructure for 1000 recipients/min SMS enforcement

## Task Commits

Each task was committed atomically:

1. **Task 1: API key self-service endpoints and revocation** - `0a5f34e` (feat)
2. **Task 2: Rate limiting (greedy bucket, client_id identifier) and X-Request-ID response header** - `57e36ae` (feat)

**Plan metadata:** _(docs commit follows this SUMMARY)_

## Files Created/Modified
- `src/main/java/com/softropic/sendam/client/api/ClientApiKeyResource.java` - POST/GET/DELETE /v1/api/keys with @RateLimited(10 req/s)
- `src/main/java/com/softropic/sendam/client/contract/ApiKeyDto.java` - Response DTO for key listings (id, label, status, createdDate — no raw value)
- `src/main/java/com/softropic/sendam/client/contract/CreateKeyRequest.java` - Request body record for key creation label
- `src/main/java/com/softropic/sendam/client/service/ApiKeyService.java` - Added createKey, listKeys, revokeKey methods
- `src/main/java/com/softropic/sendam/security/infrastructure/filter/RequestIdResponseFilter.java` - OncePerRequestFilter writing X-Request-ID from MDC on every response
- `src/main/java/com/softropic/sendam/security/service/RateLimitingService.java` - Refill.greedy for sub-60s windows; N-token tryConsume overload for Phase 3
- `src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java` - client_id identifier for ROLE_API_CLIENT, IP fallback for others
- `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` - Added requestIdResponseFilter() FilterRegistrationBean at HIGHEST_PRECEDENCE + 1

## Decisions Made
- **ResourceNotFoundException signature adapted:** The existing `ResourceNotFoundException` requires two constructor arguments (`msg`, `resourceName`). Plan showed single-arg usage. Used `"api-key"` as `resourceName` for both missing and cross-client cases — consistent with the obscure-ownership intent.
- **RequestIdResponseFilter registered globally via FilterRegistrationBean:** Same pattern as ForwardedHeaderFilter. Avoids adding it per-chain, ensuring X-Request-ID appears even on error paths that bypass security chains.
- **Refill.greedy for sub-60s windows:** `Refill.intervally(10, 1s)` allows 10 tokens to accumulate, then 10 requests fire instantly after any quiet second — this defeats the per-second rate limit intent. `Refill.greedy` distributes refill tokens smoothly.
- **N-token overload in Phase 1, enforcement in Phase 3:** The bucket infrastructure is the natural responsibility of RateLimitingService. The enforcement wiring belongs where recipient count is known — the SMS send endpoint (Phase 3).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ResourceNotFoundException constructor signature mismatch**
- **Found during:** Task 1 (ApiKeyService.revokeKey)
- **Issue:** Plan snippet used single-arg `new ResourceNotFoundException("API key not found")` but the existing class requires `(String msg, String resourceName)` — single-arg constructor does not exist
- **Fix:** Used `new ResourceNotFoundException("API key not found", "api-key")` for both missing and cross-client cases
- **Files modified:** src/main/java/com/softropic/sendam/client/service/ApiKeyService.java
- **Verification:** `mvn compile` passes cleanly
- **Committed in:** `0a5f34e` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — Bug: constructor signature adaptation)
**Impact on plan:** Minimal — identical runtime behaviour; `resourceName` field is internal metadata only.

## Issues Encountered

None.

## User Setup Required

None — no new external services or environment variables required beyond what was documented in Plan 02 (`APIKEY_PEPPER`).

## Next Phase Readiness

- **Phase 1 complete:** All Phase 1 requirements satisfied — AUTH-01 through AUTH-05 (10 req/s half), APIKEY-01, APIKEY-02, APIKEY-03, ADMIN-01
- **AUTH-05 (1000 recipients/min):** N-token bucket infrastructure is in RateLimitingService. Phase 3 SMS send endpoint wires the enforcement.
- **Phase 2 (Ledger & Credits):** No blockers from Phase 1. The client_id principal from ROLE_API_CLIENT is available in SecurityContext for Phase 2 credit reservation.

---
*Phase: 01-client-api-key-auth*
*Completed: 2026-03-10*
