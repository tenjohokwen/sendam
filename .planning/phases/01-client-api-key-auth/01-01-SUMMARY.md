---
phase: 01-client-api-key-auth
plan: "01"
subsystem: database
tags: [flyway, jpa, spring-data, hibernate, tsid, lombok, superbuilder, postgresql]

# Dependency graph
requires: []
provides:
  - main.client_account and main.client_api_key tables via Flyway V2 migration
  - ClientEntity and ClientApiKeyEntity JPA entities mapped to those tables
  - ClientRepository and ClientApiKeyRepository Spring Data repositories
  - ClientService with createClient stub (key generation is Plan 02 placeholder)
  - POST /api/admin/clients endpoint returning 201 with clientId, apiKeyId, rawApiKey
  - ROLE_ADMIN-only protection on /api/admin/clients/** via AppEndpoints.SECURED_MAPPINGS
affects:
  - 01-02 (ApiKeyService replaces stub in ClientService)
  - 01-03 (ApiKeyAuthenticationFilter uses ClientApiKeyRepository.findByKeyPrefix)
  - All subsequent phases that use client_id as persistent identity anchor

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flyway migration in schema main (V2__client_api_key.sql) — all future table DDL follows V-prefix naming"
    - "JPA entities extend AbstractAuditingEntity via @SuperBuilder — new entities follow this pattern"
    - "TSID primary keys from BaseEntity @Tsid — do NOT use @GeneratedValue(AUTO) for new entities"
    - "AppEndpoints.SECURED_MAPPINGS drives SecuredHttpEndpointGuard — add new protected paths here, not inline in SecurityConfiguration"

key-files:
  created:
    - src/main/resources/db/migration/V2__client_api_key.sql
    - src/main/java/com/softropic/sendam/client/repo/ClientEntity.java
    - src/main/java/com/softropic/sendam/client/repo/ClientRepository.java
    - src/main/java/com/softropic/sendam/client/repo/ClientApiKeyEntity.java
    - src/main/java/com/softropic/sendam/client/repo/ClientApiKeyRepository.java
    - src/main/java/com/softropic/sendam/client/contract/CreateClientRequest.java
    - src/main/java/com/softropic/sendam/client/contract/CreateClientResponse.java
    - src/main/java/com/softropic/sendam/client/service/ClientService.java
    - src/main/java/com/softropic/sendam/client/api/AdminClientResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "Stub key generation (STUB_REPLACE_IN_PLAN_02) intentional — Plan 01 establishes schema + endpoint; Plan 02 adds HMAC key generation"
  - "ADMIN_CLIENTS added to SECURED_MAPPINGS (not PUBLIC_ENDPOINTS) — ROLE_ADMIN only, no self-service"
  - "client module created as top-level sibling to security/common/email — owns its own repo/service/api/contract sub-packages"
  - "ClientApiKeyEntity does not have a rawKey field — raw key is never persisted anywhere"

patterns-established:
  - "Pattern: New protected API paths are registered in AppEndpoints.SECURED_MAPPINGS with an authority array — SECURED_ENDPOINTS is derived automatically from keySet()"
  - "Pattern: New JPA entities extend AbstractAuditingEntity, use @SuperBuilder + @NoArgsConstructor + @AllArgsConstructor, override status with @Builder.Default = EntityStatus.ACTIVE"
  - "Pattern: Service layer receives contract record (CreateClientRequest), returns contract record (CreateClientResponse) — entities never leak to API layer"

# Metrics
duration: 5min
completed: 2026-03-10
---

# Phase 1 Plan 01: Client & API Key Data Foundation Summary

**Flyway V2 migration creates client_account and client_api_key tables; JPA persistence layer and POST /api/admin/clients endpoint (stub key generation) protected by ROLE_ADMIN JWT chain**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-10T13:31:25Z
- **Completed:** 2026-03-10T13:36:44Z
- **Tasks:** 3
- **Files modified:** 10 (9 created, 1 modified)

## Accomplishments
- Flyway V2 migration creates `main.client_account` and `main.client_api_key` tables with TSID-compatible BIGINT PKs, FK constraint, UNIQUE index on `key_prefix`, and full audit columns
- `ClientEntity`, `ClientApiKeyEntity`, `ClientRepository`, `ClientApiKeyRepository` compile cleanly and map correctly to the new tables
- `POST /api/admin/clients` returns HTTP 201 with `{clientId, apiKeyId, rawApiKey}` (stub value); protected by existing JWT ROLE_ADMIN filter chain via `AppEndpoints.SECURED_MAPPINGS`

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migration V2 — client_account and client_api_key tables** - `e937145` (feat)
2. **Task 2: ClientEntity, ClientApiKeyEntity, and repositories** - `e756b37` (feat)
3. **Task 3: Contracts, ClientService stub, AdminClientResource, and AppEndpoints** - `e748edb` (feat)

**Plan metadata:** _(docs commit follows this SUMMARY)_

## Files Created/Modified
- `src/main/resources/db/migration/V2__client_api_key.sql` - Creates client_account and client_api_key tables with audit columns, FK, UNIQUE index
- `src/main/java/com/softropic/sendam/client/repo/ClientEntity.java` - JPA entity for client_account; extends AbstractAuditingEntity; status defaults to ACTIVE
- `src/main/java/com/softropic/sendam/client/repo/ClientRepository.java` - Spring Data JpaRepository for ClientEntity
- `src/main/java/com/softropic/sendam/client/repo/ClientApiKeyEntity.java` - JPA entity for client_api_key; stores clientId, keyPrefix (unique), keyHash, label; no rawKey field
- `src/main/java/com/softropic/sendam/client/repo/ClientApiKeyRepository.java` - Repository with findByKeyPrefix and findAllByClientId
- `src/main/java/com/softropic/sendam/client/contract/CreateClientRequest.java` - Request record with @NotBlank @Size validation on name
- `src/main/java/com/softropic/sendam/client/contract/CreateClientResponse.java` - Response record with clientId, apiKeyId, rawApiKey
- `src/main/java/com/softropic/sendam/client/service/ClientService.java` - @Transactional service; saves client + key stub; Plan 02 replaces stub
- `src/main/java/com/softropic/sendam/client/api/AdminClientResource.java` - @RestController at /api/admin/clients; POST returns 201
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - Added ADMIN_CLIENTS constant and ROLE_ADMIN-only entry to SECURED_MAPPINGS

## Decisions Made
- **Stub key generation is intentional:** `ClientService.createClient` writes placeholder prefix/hash values (`snd_stub_prefix` / `stub_hash`). This is the designed end state for Plan 01. Plan 02 adds `ApiKeyService` with real HMAC-SHA256 generation and replaces these stubs.
- **ROLE_ADMIN only on `/api/admin/clients/**`:** No `ROLE_LTD_ADMIN` or `ROLE_USER` access to client creation. Consistent with admin-only onboarding decision in PROJECT.md.
- **`client` module as top-level package:** Sibling to `security`, `common`, `email` — not nested inside `security`. Owns its own `api/`, `service/`, `repo/`, `contract/` sub-packages.
- **`key_hash` VARCHAR(64):** HMAC-SHA256 always produces 64 hex characters. Column size is exact, not padded.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. Database migration runs automatically via Flyway on next application startup.

## Next Phase Readiness

- **Ready for Plan 02:** `ClientApiKeyRepository`, `ClientApiKeyEntity`, and `ClientService` are in place. Plan 02 adds `ApiKeyService` with HMAC-SHA256 key generation and replaces the stub in `ClientService.createClient`.
- **Blocker:** Stub key values (`snd_stub_prefix` / `stub_hash`) in DB after running Plan 01 are not valid for API key authentication. This is expected — the system is not independently usable until Plan 02 completes. Plans 01+02 are an atomic two-plan operation.
- **No DB migration changes expected in Plan 02** — schema from V2 is complete.

---
*Phase: 01-client-api-key-auth*
*Completed: 2026-03-10*
