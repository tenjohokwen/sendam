---
phase: 01-client-api-key-auth
plan: "02"
subsystem: auth
tags: [spring-security, hmac-sha256, commons-codec, bearer-token, filter-chain, api-key, security-filter-chain]

# Dependency graph
requires:
  - phase: 01-01
    provides: ClientApiKeyEntity, ClientApiKeyRepository with findByKeyPrefix, ClientService stub in place

provides:
  - ApiKeyService with HMAC-SHA256 key generation (snd_<prefix><secret> format) and constant-time verify
  - ApiKeyCreationResult value record returned from generateAndPersist
  - ApiKeyAuthenticationFilter: OncePerRequestFilter reading Bearer header, calling apiKeyService.authenticate
  - ClientSecurityConfiguration: @Order(1) SecurityFilterChain scoped to /v1/api/**
  - ClientService stub replaced with real apiKeyService.generateAndPersist call
  - application.yaml apikey.pepper config with APIKEY_PEPPER env var override
  - AppEndpoints.CLIENT_API constant (/v1/api/**)

affects:
  - 01-03 (rate limiting, health endpoint — builds on /v1/api/** chain established here)
  - All subsequent phases using ROLE_API_CLIENT authority from the authenticated SecurityContext

# Tech tracking
tech-stack:
  added:
    - commons-codec HmacUtils (already in pom.xml — used for hmacSha256Hex)
  patterns:
    - "@Order(1) / @Order(2) dual SecurityFilterChain — client API chain claims /v1/api/**, JWT chain handles everything else"
    - "ApiKeyAuthenticationFilter NOT @Component — instantiated manually in ClientSecurityConfiguration to prevent global servlet filter registration"
    - "HMAC-SHA256 with server pepper stored in application.yaml (APIKEY_PEPPER env var) — no BCrypt for API key hashing"
    - "Constant-time MessageDigest.isEqual comparison in authenticate() — prevents timing attacks"
    - "DB read on every authenticate() call — no caching (AUTH-04 requirement)"

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/contract/ApiKeyCreationResult.java
    - src/main/java/com/softropic/sendam/client/service/ApiKeyService.java
    - src/main/java/com/softropic/sendam/client/infrastructure/filter/ApiKeyAuthenticationFilter.java
    - src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/ClientService.java
    - src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java
    - src/main/resources/application.yaml

key-decisions:
  - "ApiKeyAuthenticationFilter not @Component — prevents Spring Boot auto-registering it as a global servlet filter outside the security chain"
  - "@Order(1) on ClientSecurityConfiguration, @Order(2) on existing SecurityConfiguration.filterChain — explicit ordering required; without it both chains get Integer.MAX_VALUE and behavior is undefined"
  - "HMAC-SHA256 (not BCrypt) for API key hashing — deterministic hash enables prefix lookup + constant-time comparison; BCrypt is not appropriate for token hashing"
  - "apikey.pepper from APIKEY_PEPPER env var — must be overridden in production; fallback 'change-me-in-production' is documented as unsafe"

patterns-established:
  - "Pattern: New filter-chain scoped endpoints get their own @Configuration class at @Order(N) — not added to SecurityConfiguration"
  - "Pattern: HandlerExceptionResolver-based exception handling (AuthenticationExceptionHandler, ApplicationAccessDeniedHandler) is reused across chains — no new handler classes needed"

# Metrics
duration: 4min
completed: 2026-03-10
---

# Phase 1 Plan 02: API Key Service and Client Security Filter Chain Summary

**HMAC-SHA256 API key generation (snd_<prefix><secret>) with ApiKeyAuthenticationFilter and a dedicated @Order(1) SecurityFilterChain scoped to /v1/api/***

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-10T13:39:27Z
- **Completed:** 2026-03-10T13:43:00Z
- **Tasks:** 2
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments
- `ApiKeyService.generateAndPersist` creates `snd_<16hexPrefix><43b64urlSecret>` keys (~63 chars), stores only the prefix and HMAC-SHA256 hash in DB — raw key never persisted
- `ApiKeyService.authenticate` performs prefix lookup, ACTIVE status check, and constant-time HMAC comparison — no caching, live DB read per request (AUTH-04)
- `ApiKeyAuthenticationFilter` + `ClientSecurityConfiguration` at `@Order(1)` establish a dedicated security chain for `/v1/api/**`; the existing JWT chain is bumped to `@Order(2)` with an explicit annotation
- `ClientService` stub replaced with real `apiKeyService.generateAndPersist` call

## Task Commits

Each task was committed atomically:

1. **Task 1: ApiKeyService HMAC-SHA256 key generation and verification** - `24a816a` (feat)
2. **Task 2: ApiKeyAuthenticationFilter and ClientSecurityConfiguration @Order(1) chain** - `0d43394` (feat)

**Plan metadata:** _(docs commit follows this SUMMARY)_

## Files Created/Modified
- `src/main/java/com/softropic/sendam/client/contract/ApiKeyCreationResult.java` - Value record (apiKeyId, rawKey) returned from generateAndPersist
- `src/main/java/com/softropic/sendam/client/service/ApiKeyService.java` - HMAC-SHA256 key generation and Bearer token verification
- `src/main/java/com/softropic/sendam/client/infrastructure/filter/ApiKeyAuthenticationFilter.java` - OncePerRequestFilter; validates Bearer header, delegates to apiKeyService.authenticate
- `src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java` - @Order(1) SecurityFilterChain scoped to /v1/api/**; adds ApiKeyAuthenticationFilter
- `src/main/java/com/softropic/sendam/client/service/ClientService.java` - Stub replaced; injects ApiKeyService; calls generateAndPersist
- `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` - Added @Order(2) to filterChain bean
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - Added CLIENT_API = "/v1/api/**" constant
- `src/main/resources/application.yaml` - Added apikey.pepper config with APIKEY_PEPPER env var and production warning comment

## Decisions Made
- **No @Component on ApiKeyAuthenticationFilter:** Avoids Spring Boot's auto-registration of @Component filters as global servlet filters — the filter must only run in the `/v1/api/**` chain.
- **@Order(2) on existing filterChain:** Without this annotation, both chains default to `Integer.MAX_VALUE` ordering and the chain selected for `/v1/api/**` is non-deterministic. Explicit ordering is required.
- **HMAC-SHA256, not BCrypt:** BCrypt is not suitable for API key hashing. A deterministic HMAC allows constant-time prefix+hash verification without storing the raw key.
- **pepper via APIKEY_PEPPER env var:** Follows the existing `${ENV_VAR:fallback}` convention in application.yaml. The fallback value is documented as unsafe for production.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

**Environment variable required in production:**

```
APIKEY_PEPPER=<random 32+ char string>
```

The application uses `change-me-in-production` as a default fallback. This MUST be overridden before any real API keys are generated in production. Keys generated with the default pepper will have weaker security.

## Next Phase Readiness

- **Ready for Plan 03:** The `/v1/api/**` security chain is live. A valid `snd_...` Bearer token now sets a `SecurityContext` with `clientId` principal and `ROLE_API_CLIENT` authority. Plan 03 can add rate limiting and the health endpoint against this chain.
- **No blockers:** Plans 01+02 complete the ADMIN-01 requirement. POST /api/admin/clients now returns a real snd_... API key, and that key can authenticate against /v1/api/** endpoints.

---
*Phase: 01-client-api-key-auth*
*Completed: 2026-03-10*
