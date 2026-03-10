# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 1 — Client & API Key Authentication

## Current Position

Phase: 1 of 5 (Client & API Key Authentication)
Plan: 02 of 3 (01-02 complete)
Status: In progress
Last activity: 2026-03-10 — Completed 01-02-PLAN.md (HMAC-SHA256 key generation, ApiKeyAuthenticationFilter, @Order(1) security chain)

Progress: ██████░░░░ 67%

## Performance Metrics

**Velocity:**
- Total plans completed: 2
- Average duration: 4.5 min
- Total execution time: 9 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-client-api-key-auth | 2 | 9 min | 4.5 min |

**Recent Trend:**
- Last 5 plans: 5 min, 4 min
- Trend: stable

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Ledger-first balance: balance derived from ledger entries, never a mutable column
- Atomic reservation: credits reserved synchronously at send time
- Admin-only onboarding: no self-registration
- Nexah only: no provider abstraction layer in v1
- Stub key generation in Plan 01 is intentional — Plans 01+02 are an atomic two-plan operation for ADMIN-01
- ROLE_ADMIN only on /api/admin/clients/** — no LTD_ADMIN or USER access to client creation
- `client` module is top-level sibling to security/common/email — owns api/service/repo/contract sub-packages
- rawKey never stored anywhere — not in entity, not in DB; returned to caller once only
- ApiKeyAuthenticationFilter not @Component — prevents Spring Boot global servlet filter registration; instantiated manually in ClientSecurityConfiguration
- @Order(1)/@Order(2) on dual SecurityFilterChains — explicit ordering required; without it Integer.MAX_VALUE on both causes undefined behavior for /v1/api/**
- HMAC-SHA256 (not BCrypt) for API key hashing — deterministic hash enables prefix lookup + constant-time comparison
- APIKEY_PEPPER env var must be set in production; fallback 'change-me-in-production' is documented as unsafe

### Pending Todos

- Plan 03: Add rate limiting and GET /v1/api/health endpoint (builds on /v1/api/** chain from Plan 02)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — the fallback default is documented as unsafe.

## Session Continuity

Last session: 2026-03-10T13:43:00Z
Stopped at: Completed 01-02-PLAN.md
Resume file: None
