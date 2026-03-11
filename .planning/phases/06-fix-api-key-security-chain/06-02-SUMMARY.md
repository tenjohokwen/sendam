---
phase: 06-fix-api-key-security-chain
plan: 02
subsystem: auth
tags: [spring-security, api-key, integration-test, hibernate, lombok, field-shadowing]

# Dependency graph
requires:
  - phase: 06-fix-api-key-security-chain
    provides: "06-01: CLIENT_API_KEYS constant, ClientSecurityConfiguration matcher fix, ClientApiKeySecurityIT negative-path tests"
  - phase: 01-client-api-key-auth
    provides: ApiKeyService.createKey(), ApiKeyAuthenticationFilter, client_api_key table
provides:
  - clientApiKeysWithValidTokenReturns200 positive-path integration test in ClientApiKeySecurityIT
  - Fix for ClientApiKeyEntity Lombok @Builder.Default status field shadowing bug (Hibernate returns INACTIVE for all loaded keys)
  - JWT secret seeding pattern in @BeforeEach for API key IT suite
affects:
  - Any future IT that seeds API key entities and makes authenticated HTTP requests

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "IT tests that make HTTP requests through SecurityAdviceFilter must seed the JWT secret (main.sec) with ON CONFLICT DO NOTHING before any HTTP request"
    - "Child entities extending AbstractAuditingEntity must NOT re-declare 'status' field — the @Builder.Default in the parent is sufficient; Hibernate field-access maps the parent's @Column-annotated field"
    - "@BeforeEach fixture uses transactionTemplate.execute() for direct JDBC inserts, then calls @Transactional service outside the template to ensure FK row is committed first"

key-files:
  created: []
  modified:
    - src/test/java/com/softropic/sendam/gateway/auth/api/ClientApiKeySecurityIT.java
    - src/main/java/com/softropic/sendam/gateway/auth/repo/ClientApiKeyEntity.java

key-decisions:
  - "ClientApiKeyEntity.status shadowing field removed — child entities must not re-declare fields already mapped in @MappedSuperclass parents; only AbstractAuditingEntity owns the JPA 'status' column mapping"
  - "JWT secret seeded with ON CONFLICT (version, bus_id) DO NOTHING — idempotent across all three @BeforeEach calls; JwtSecretService caches after first fetch so subsequent tests never re-hit DB"
  - "Positive-path IT fixture uses ApiKeyService.createKey() (not raw JDBC insert) — ensures key hash is computed with same pepper as authenticate(), making the round-trip self-consistent"

patterns-established:
  - "IT suites making HTTP requests through SecurityAdviceFilter: seed JWT secret in @BeforeEach using ON CONFLICT DO NOTHING"

# Metrics
duration: 26min
completed: 2026-03-11
---

# Phase 6 Plan 02: Fix API Key Security Chain (Gap Closure) Summary

**Closed the 06-VERIFICATION.md gap by adding `clientApiKeysWithValidTokenReturns200` to ClientApiKeySecurityIT, fixing a Hibernate field-shadowing bug in ClientApiKeyEntity that caused all DB-loaded API keys to appear INACTIVE**

## Performance

- **Duration:** 26 min
- **Started:** 2026-03-11T15:21:06Z
- **Completed:** 2026-03-11T15:47:00Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments

- Added `clientApiKeysWithValidTokenReturns200` @Test method with @BeforeEach/@AfterEach fixture that seeds a real `client_account` and `client_api_key` via `ApiKeyService.createKey()`, then sends `GET /v1/api/keys` with a valid Bearer token and asserts HTTP 200 with non-null JSON array body
- Fixed production bug in `ClientApiKeyEntity`: the re-declared `@Builder.Default protected EntityStatus status` shadowed `AbstractAuditingEntity.status`; Hibernate (field-access) was mapping the DB `status=ACTIVE` column to the child's unannotated field while `getStatus()` read the parent's field that retained `INACTIVE` from the `@NoArgsConstructor` — causing every DB-loaded API key to appear revoked
- Established the pattern for seeding the JWT secret (`main.sec`) in ITs that make HTTP requests through the global `SecurityAdviceFilter`

## Task Commits

Each task was committed atomically:

1. **Task 1: Add positive-path test with client fixture to ClientApiKeySecurityIT** - `b811ef4` (test)

**Plan metadata:** (to be added in final commit)

## Files Created/Modified

- `src/test/java/com/softropic/sendam/gateway/auth/api/ClientApiKeySecurityIT.java` - Added @BeforeEach (seeds JWT secret + client_account + client_api_key), @AfterEach (cleanup), and clientApiKeysWithValidTokenReturns200 test
- `src/main/java/com/softropic/sendam/gateway/auth/repo/ClientApiKeyEntity.java` - Removed shadowing `@Builder.Default protected EntityStatus status` field and unused EntityStatus import

## Decisions Made

- Removed `@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE` from `ClientApiKeyEntity` entirely — `ApiKeyService.generateAndPersist()` explicitly calls `.status(EntityStatus.ACTIVE)` on the builder, so the default is redundant; the parent's `@Builder.Default` default (`INACTIVE`) is never reached because the builder always specifies the status explicitly
- Used `ON CONFLICT (version, bus_id) DO NOTHING` for JWT secret seeding — makes `@BeforeEach` idempotent across all 3 test methods; `JwtSecretService` caches after first fetch so only the first test ever performs a DB lookup

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Hibernate field-shadowing bug in ClientApiKeyEntity causing all loaded API keys to appear INACTIVE**

- **Found during:** Task 1 (Add positive-path test) — test was getting 401 "API key is revoked" even though the DB insert showed ACTIVE status
- **Issue:** `ClientApiKeyEntity` declared `@Builder.Default protected EntityStatus status = EntityStatus.ACTIVE` which shadowed `AbstractAuditingEntity.status`. With Hibernate field-access, Hibernate mapped the `status` DB column to the child's field (no `@Column` annotation on the child field, but same name wins in child-class field scanning). `getStatus()` in the parent method read the parent's `status` field which stayed `INACTIVE` (set by `AbstractAuditingEntity.@NoArgsConstructor` via `$default$status() = INACTIVE`). Result: every entity loaded from DB via `findByKeyPrefix()` returned `INACTIVE` from `getStatus()`, causing `authenticate()` to throw "API key is revoked" for ALL valid keys.
- **Fix:** Removed the shadowing `protected EntityStatus status` field and `@Builder.Default` / `Builder` import from `ClientApiKeyEntity`. The parent's JPA-annotated field is now the only `status` field, Hibernate maps the column correctly, and `getStatus()` returns the actual DB value.
- **Files modified:** `src/main/java/com/softropic/sendam/gateway/auth/repo/ClientApiKeyEntity.java`
- **Verification:** All 3 tests pass with 0 failures; positive-path test asserts HTTP 200 with a real DB-loaded entity.
- **Committed in:** b811ef4 (Task 1 commit)

**2. [Rule 2 - Missing Critical] Added JWT secret seeding to @BeforeEach for SecurityAdviceFilter compatibility**

- **Found during:** Task 1 — after fixing the entity bug, the positive test still got 401 with "Internal unknown exception"; server log showed `SecException(KEY_NOT_FOUND)` from `SecurityAdviceFilter.doFilterInternal()` → `JwtSecretService.addSecretToThread()` → `SecretService.fetchSecret(v1, jot)`
- **Issue:** `SecurityAdviceFilter` is `@Component` (global servlet filter, runs on ALL requests). It calls `JwtSecretService.addSecretToThread()` which fetches the JWT signing secret from `main.sec`. The test DB had no JWT secret seeded, so every HTTP request to the test server threw `SecException(KEY_NOT_FOUND)` → `ApiAdvice.secExceptionHandler()` → 401. The two negative-path tests happen to expect 401 (for different reasons) so they passed; the positive test required 200 but got 401 from this unrelated cause.
- **Fix:** Added `INSERT INTO main.sec ... ON CONFLICT (version, bus_id) DO NOTHING` to `@BeforeEach`, seeding the same JWT secret row used by all other ITs (`/sql/secData.sql`). Once fetched, `JwtSecretService` caches the secret in a `volatile Secret secret` field, so subsequent requests within the same Spring context never re-query.
- **Files modified:** `src/test/java/com/softropic/sendam/gateway/auth/api/ClientApiKeySecurityIT.java`
- **Verification:** All 3 tests pass; no more `SecException` in server logs.
- **Committed in:** b811ef4 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 1 bug, 1 Rule 2 missing critical)
**Impact on plan:** The entity bug fix is a genuine production bug — API key authentication was broken for all DB-loaded keys (only worked when the entity was freshly built via the builder in the same transaction). The JWT secret seeding is a test infrastructure requirement. Both are essential for correctness and test reliability. No scope creep.

## Issues Encountered

The two deviations above were the main issues discovered during execution. Both were diagnosed through careful examination of the Hibernate SELECT logs and server-side exception stack traces. The root cause of each was non-obvious:

1. **Entity bug** — the `@Builder.Default` + `@SuperBuilder` + field shadowing combination is a known Lombok/Hibernate gotcha; Hibernate field-access scans the full class hierarchy and the child's same-named field won without the `@Column` annotation, silently breaking all authentication.

2. **JWT secret** — `SecurityAdviceFilter` is `@Component` and runs before any Security filter chain, making it invisible to tests that only configure chain-specific filters; the other ITs use `@Sql("/sql/secData.sql")` to address this but the `ClientApiKeySecurityIT` was written without that context.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 06-VERIFICATION.md gap is closed: `clientApiKeysWithValidTokenReturns200` exists and asserts HTTP 200 with a real Bearer API key
- All 5 truths from the verification report are now fully verified by automation
- The `ClientApiKeyEntity` entity bug fix means API key authentication now works correctly in all contexts (not just the builder path)
- v1.0 milestone is fully complete — all phases done

---
*Phase: 06-fix-api-key-security-chain*
*Completed: 2026-03-11*
