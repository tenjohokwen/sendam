---
phase: 01-client-api-key-auth
verified: 2026-03-11T02:37:01Z
status: gaps_found
score: 4/5 must-haves verified
gaps:
  - truth: "Client making more than 10 req/s receives HTTP 429 on the 11th request"
    status: failed
    reason: "RateLimitingAspect throws AuthorizationException(TOO_MANY_REQUESTS) but ApiAdvice maps ALL AuthorizationException to HTTP 401, not 429. Only RateLimitExceededException gets HTTP 429."
    artifacts:
      - path: "src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java"
        issue: "Line 60: throws AuthorizationException — wrong exception type for rate limiting"
      - path: "src/main/java/com/softropic/sendam/security/api/ApiAdvice.java"
        issue: "Lines 115-122: @ExceptionHandler(AuthorizationException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED) — 10 req/s limit produces 401, not 429"
    missing:
      - "RateLimitingAspect must throw RateLimitExceededException (not AuthorizationException) so ApiAdvice's existing 429 handler fires"
      - "Alternatively add a dedicated @ExceptionHandler for AuthorizationException with SecurityError.TOO_MANY_REQUESTS that returns 429"
---

# Phase 1: Client & API Key Authentication Verification Report

**Phase Goal:** Establish client identity, API key authentication, and rate limiting — everything else depends on knowing who the caller is.
**Verified:** 2026-03-11T02:37:01Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Admin can create a client account and the first API key; raw key value is shown once | VERIFIED | `AdminClientResource.createClient` → `ClientService.createClient` → `ApiKeyService.generateAndPersist` returns raw key in `CreateClientResponse.rawApiKey`; key never persisted (no rawKey field on entity) |
| 2 | API requests authenticated via Bearer key are accepted; invalid or revoked keys rejected (403) | VERIFIED | `ApiKeyAuthenticationFilter` extracts Bearer token, calls `ApiKeyService.authenticate`; ACTIVE check at line 120; constant-time HMAC comparison; `AuthorizationException` propagated and resolved via `handlerExceptionResolver` |
| 3 | Gateway derives client_id from the API key; clients do not send it | VERIFIED | `ApiKeyService.authenticate` sets principal = `keyEntity.getClientId()` (Long) on `UsernamePasswordAuthenticationToken`; `ClientApiKeyResource.getClientId()` reads from SecurityContext |
| 4 | Every API response carries an X-Request-ID trace header | VERIFIED | `RequestIdResponseFilter` (OncePerRequestFilter) reads MDC, writes `X-Request-ID`; registered globally via `FilterRegistrationBean` at `HIGHEST_PRECEDENCE + 1` in `SecurityConfiguration.requestIdResponseFilter()` |
| 5 | Clients exceeding 10 req/s or 1000 recipients/min receive HTTP 429 | FAILED | 1000 recipients/min path is correct (RateLimitExceededException → 429 via ApiAdvice). 10 req/s path is broken: RateLimitingAspect throws AuthorizationException → ApiAdvice maps to HTTP 401 UNAUTHORIZED, not 429 |

**Score:** 4/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V2__client_api_key.sql` | client_account and client_api_key tables | VERIFIED | Both tables present; FK constraint, UNIQUE on key_prefix, no raw key column |
| `src/main/java/com/softropic/sendam/client/repo/ClientEntity.java` | JPA entity for client_account | VERIFIED | @Entity, @Table(schema="main"), @SuperBuilder, status defaults ACTIVE |
| `src/main/java/com/softropic/sendam/client/repo/ClientApiKeyEntity.java` | JPA entity for client_api_key | VERIFIED | clientId, keyPrefix (unique), keyHash, label; no rawKey field; status ACTIVE by default |
| `src/main/java/com/softropic/sendam/client/repo/ClientApiKeyRepository.java` | findByKeyPrefix, findAllByClientId | VERIFIED | Both query methods present |
| `src/main/java/com/softropic/sendam/client/service/ApiKeyService.java` | HMAC-SHA256 generation + authenticate | VERIFIED | generateAndPersist: snd_ + 16hex + 43b64url; authenticate: prefix lookup, ACTIVE check, constant-time MessageDigest.isEqual; no caching (AUTH-04) |
| `src/main/java/com/softropic/sendam/client/infrastructure/filter/ApiKeyAuthenticationFilter.java` | OncePerRequestFilter for Bearer tokens | VERIFIED | Reads Authorization header, delegates to apiKeyService.authenticate, sets SecurityContext; NOT @Component |
| `src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java` | @Order(1) chain for /v1/** | VERIFIED | @Order(1), securityMatcher CLIENT_API (/v1/**), all requests require auth, ApiKeyAuthenticationFilter inserted |
| `src/main/java/com/softropic/sendam/client/api/AdminClientResource.java` | POST /api/admin/clients | VERIFIED | @RestController, @RequestMapping("/api/admin/clients"), delegates to ClientService |
| `src/main/java/com/softropic/sendam/client/api/ClientApiKeyResource.java` | POST/GET/DELETE /v1/api/keys | VERIFIED | All three endpoints present; @RateLimited(10 req/s); raw key returned on POST only; no raw value on GET |
| `src/main/java/com/softropic/sendam/security/infrastructure/filter/RequestIdResponseFilter.java` | Global X-Request-ID response header | VERIFIED | OncePerRequestFilter; reads MDC(REQUEST_ID_NAME); writes header; registered via FilterRegistrationBean |
| `src/main/java/com/softropic/sendam/security/service/RateLimitingService.java` | Refill.greedy for sub-60s; N-token overload | VERIFIED | greedy for window < 60s; intervally otherwise; N-token tryConsume overload present |
| `src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java` | @RateLimited AOP enforcement + client_id identifier | PARTIAL | client_id identifier for ROLE_API_CLIENT is correct. Exception type is wrong: throws AuthorizationException → HTTP 401, not 429 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AdminClientResource | ClientService | constructor injection | WIRED | `clientService.createClient(request)` |
| ClientService | ApiKeyService | constructor injection | WIRED | `apiKeyService.generateAndPersist(client.getId(), ...)` |
| ApiKeyService | ClientApiKeyRepository | Spring Data | WIRED | `repository.findByKeyPrefix`, `repository.save` |
| ApiKeyAuthenticationFilter | ApiKeyService | constructor injection | WIRED | `apiKeyService.authenticate(rawKey)` |
| ClientSecurityConfiguration | ApiKeyAuthenticationFilter | manual instantiation | WIRED | `new ApiKeyAuthenticationFilter(apiKeyService, resolver)` |
| SecurityConfiguration | RequestIdResponseFilter | FilterRegistrationBean | WIRED | `requestIdResponseFilter()` bean at HIGHEST_PRECEDENCE + 1 |
| RateLimitingAspect | RateLimitingService | constructor injection | WIRED | `rateLimitingService.tryConsume(...)` |
| RateLimitingAspect | ApiAdvice (429 path) | exception type | NOT WIRED | Throws AuthorizationException; ApiAdvice catches it at HTTP 401 handler. RateLimitExceededException (429) is never thrown by the aspect |
| SmsService (1000/min path) | ApiAdvice (429 path) | RateLimitExceededException | WIRED | Line 94: throws RateLimitExceededException → @ResponseStatus(429) in ApiAdvice |
| AppEndpoints.ADMIN_CLIENTS | SecurityConfiguration | SECURED_MAPPINGS | WIRED | Entry: ADMIN_CLIENTS → ROLE_ADMIN in static initializer |

### Requirements Coverage

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| AUTH-01: Bearer API key authentication | SATISFIED | ApiKeyAuthenticationFilter + ApiKeyService.authenticate fully wired |
| AUTH-02: Gateway derives client_id from key | SATISFIED | authenticate() sets clientId as principal; no client_id in request body |
| AUTH-03: X-Request-ID on every response | SATISFIED | RequestIdResponseFilter globally registered |
| AUTH-04: Revoked keys immediately rejected | SATISFIED | No caching; live DB read; INACTIVE check throws AuthorizationException |
| AUTH-05: 10 req/s or 1000 recipients/min → 429 | BLOCKED | 10 req/s path returns 401 instead of 429; 1000/min path returns 429 correctly |
| APIKEY-01: Create key; raw value shown once | SATISFIED | POST /v1/api/keys returns ApiKeyCreationResult(rawKey); GET returns ApiKeyDto (no rawKey) |
| APIKEY-02: List keys (no raw value) | SATISFIED | ApiKeyDto: id, label, status, createdDate — no rawKey |
| APIKEY-03: Revoke key; immediately loses access | SATISFIED | revokeKey sets INACTIVE; next authenticate() live-reads INACTIVE → rejects |
| ADMIN-01: Admin creates client + first API key | SATISFIED | POST /api/admin/clients → ROLE_ADMIN only; returns clientId, apiKeyId, rawApiKey |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/java/com/softropic/sendam/security/infrastructure/RateLimitingAspect.java` | 60 | Wrong exception type: `AuthorizationException` thrown for rate limit exceeded | Blocker | 10 req/s rate limit produces HTTP 401 instead of HTTP 429; AUTH-05 partially fails |
| `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` | 26 | TODO: `FROM_CHROME` comment about investigation needed | Warning | Not blocking; Chrome DevTools endpoint handling is an open question |
| `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java` | 99 | TODO comment in authorizationManager | Warning | Not blocking; internal role naming note |

### Human Verification Required

None — all remaining verification is structural and has been covered programmatically.

## Gaps Summary

One gap blocks full goal achievement:

**AUTH-05 (10 req/s half):** The rate-limiting infrastructure is correctly built — Bucket4j greedy buckets, client_id-based identifiers, `@RateLimited` annotation on all self-service endpoints and the SMS send endpoint. However, when the bucket is exhausted, `RateLimitingAspect` throws `AuthorizationException("Too many requests...", TOO_MANY_REQUESTS)`. `ApiAdvice` has two handlers for `AuthorizationException`: one returning HTTP 401 (line 118) and one returning HTTP 403 for fraud cases (line 207). Neither returns 429. The correct exception for the 10 req/s path is `RateLimitExceededException`, which has its own `@ExceptionHandler` returning HTTP 429 (line 432). The fix is a one-line change in `RateLimitingAspect`: replace `throw new AuthorizationException(...)` with `throw new RateLimitExceededException(...)`.

The 1000 recipients/min half of AUTH-05 is correctly implemented: `SmsService.sendSms` calls `rateLimitingService.tryConsume` with the N-token overload, and throws `RateLimitExceededException` on failure, which ApiAdvice maps to HTTP 429.

---

_Verified: 2026-03-11T02:37:01Z_
_Verifier: Claude (gsd-verifier)_
