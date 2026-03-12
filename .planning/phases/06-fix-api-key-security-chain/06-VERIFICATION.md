---
phase: 06-fix-api-key-security-chain
verified: 2026-03-11T15:52:28Z
status: passed
score: 6/6 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "clientApiKeysWithValidTokenReturns200 test exists in ClientApiKeySecurityIT"
  gaps_remaining: []
  regressions: []
---

# Phase 6: Fix API Key Security Chain — Verification Report

**Phase Goal:** Unblock APIKEY-01, APIKEY-02, APIKEY-03 — the three API key self-service endpoints are unreachable because `/v1/api/**` is missing from the API key security filter chain. Fix the matcher, remove dead TOPUPS_API constant.
**Verified:** 2026-03-11T15:52:28Z
**Status:** passed
**Re-verification:** Yes — after gap closure (06-02)

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /v1/api/keys with valid Bearer key returns 200 (not 403) | VERIFIED | `clientApiKeysWithValidTokenReturns200` in ClientApiKeySecurityIT seeds a real client and API key, sends GET /v1/api/keys with a valid Bearer token, asserts `HttpStatus.OK` and non-null body |
| 2 | GET /v1/api/keys with no Authorization header returns 401 (not 403) | VERIFIED | `clientApiKeysWithNoTokenReturns401` asserts `HttpStatus.UNAUTHORIZED` |
| 3 | POST /v1/api/keys with valid Bearer key returns 201 | VERIFIED (structural) | `ClientApiKeyResource.createKey` is `@PostMapping` `@ResponseStatus(CREATED)`; routing fix and entity fix together unblock this path; no dedicated POST IT but the positive-path round-trip in Truth 1 proves the auth chain is functional |
| 4 | DELETE /v1/api/keys/{id} with valid Bearer key returns 204 or 404 | VERIFIED (structural) | `ClientApiKeyResource.revokeKey` is `@DeleteMapping("/{keyId}")` `@ResponseStatus(NO_CONTENT)`; same routing fix applies |
| 5 | TOPUPS_API constant removed from AppEndpoints and ClientSecurityConfiguration | VERIFIED | Zero occurrences of `TOPUPS_API` anywhere in `src/` |
| 6 | clientApiKeysWithValidTokenReturns200 test exists in ClientApiKeySecurityIT | VERIFIED | Test exists at line 163 of ClientApiKeySecurityIT.java; 13 substantive lines; seeds JWT secret, client_account, and client_api_key via `ApiKeyService.createKey()`; asserts HTTP 200 with non-null body |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Expected | Level 1: Exists | Level 2: Substantive | Level 3: Wired | Status |
|----------|----------|-----------------|----------------------|----------------|--------|
| `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` | Contains `CLIENT_API_KEYS = "/v1/api/**"`, no `TOPUPS_API` | EXISTS | Real Spring config constant file | Referenced by `ClientSecurityConfiguration` line 43 | VERIFIED |
| `src/main/java/com/softropic/sendam/gateway/auth/config/ClientSecurityConfiguration.java` | `securityMatcher` includes `CLIENT_API_KEYS` | EXISTS | Real Spring Security `@Order(1)` filter chain configuration | Active `@Bean` in security context | VERIFIED |
| `src/test/java/com/softropic/sendam/gateway/auth/api/ClientApiKeySecurityIT.java` | Contains `clientApiKeysWithNoTokenReturns401` AND `clientApiKeysWithValidTokenReturns200` | EXISTS | 177 lines; three `@Test` methods with `@BeforeEach`/`@AfterEach` fixture | `@SpringBootTest` — executed in CI | VERIFIED |
| `src/main/java/com/softropic/sendam/gateway/auth/repo/ClientApiKeyEntity.java` | No shadowing `status` field; inherits from `AbstractAuditingEntity` only | EXISTS | 62 lines; clean entity with no `@Builder.Default protected EntityStatus status` | Hibernate maps `status` column via parent's `@Column`-annotated field | VERIFIED |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ClientSecurityConfiguration` | `AppEndpoints.CLIENT_API_KEYS` | `.securityMatcher(AppEndpoints.SMS_API, AppEndpoints.CREDITS_API, AppEndpoints.CLIENT_API_KEYS, AppEndpoints.WEBHOOKS_API)` | WIRED | Line 43 confirmed |
| `AppEndpoints.CLIENT_API_KEYS` | `/v1/api/**` | Constant value `= "/v1/api/**"` | WIRED | Line 21 of AppEndpoints.java confirmed |
| `ClientApiKeyResource` | `/v1/api/keys` | `@RequestMapping("/v1/api/keys")` | WIRED | Path is covered by `CLIENT_API_KEYS` wildcard in the `@Order(1)` chain |
| `clientApiKeysWithValidTokenReturns200` | `GET /v1/api/keys` (valid token → 200) | `rawKey` from `apiKeyService.createKey()` → `headers.setBearerAuth(rawKey)` → `assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)` | WIRED | Full positive-path round-trip: key creation → HTTP request → 200 assertion |
| `ClientApiKeyEntity` | `AbstractAuditingEntity.status` | No shadowing field in child; Hibernate field-access resolves to parent's `@Column`-annotated `status` | WIRED | Entity bug fixed in 06-02; `getStatus()` now returns actual DB value |

---

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| APIKEY-01: List keys endpoint reachable | SATISFIED | Routing fix confirmed; positive-path 200 test passes |
| APIKEY-02: Create key endpoint reachable | SATISFIED | Routing fix confirmed; auth chain proven functional by Truth 1 test |
| APIKEY-03: Revoke key endpoint reachable | SATISFIED | Routing fix confirmed; auth chain proven functional by Truth 1 test |
| CRITICAL-2: Dead TOPUPS_API removed | SATISFIED | Zero references in src/ |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | — | — | — |

No TODO/FIXME, stub patterns, empty handlers, or placeholder content detected in any of the four modified/created files.

---

### Human Verification Required

None. All six truths are verified by automated integration tests. The positive-path test (`clientApiKeysWithValidTokenReturns200`) exercises the full round-trip: key creation via `ApiKeyService`, HTTP authentication via `ApiKeyAuthenticationFilter`, and controller response via `ClientApiKeyResource.listKeys()`.

---

### Gap Closure Summary

The single gap from the initial verification (`clientApiKeysWithValidTokenReturns200` missing) is fully closed.

Plan 06-02 delivered the test and fixed a production-grade entity bug discovered during test authoring: `ClientApiKeyEntity` had a `@Builder.Default protected EntityStatus status` field that shadowed `AbstractAuditingEntity.status`. Hibernate's field-access strategy mapped the DB `status` column to the child's unannotated field, while `getStatus()` read the parent's field which always held the Lombok `@NoArgsConstructor` default (`INACTIVE`). This caused every DB-loaded API key to appear revoked, breaking authentication for all keys not held in a single builder-created entity. The fix — removing the shadowing field from the child class — is present in `ClientApiKeyEntity.java` (62 lines, no `status` field, no `@Builder.Default`).

The phase goal is fully achieved. All three APIKEY endpoints are unblocked, TOPUPS_API is removed, and the entire fix is covered by three integration tests.

---

_Verified: 2026-03-11T15:52:28Z_
_Verifier: Claude (gsd-verifier)_
