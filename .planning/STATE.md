# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 1 — Client & API Key Authentication

## Current Position

Phase: 1 of 5 (Client & API Key Authentication)
Plan: 01 of 3 (01-01 complete)
Status: In progress
Last activity: 2026-03-10 — Completed 01-01-PLAN.md (data foundation: tables, entities, stub endpoint)

Progress: ███░░░░░░░ 33%

## Performance Metrics

**Velocity:**
- Total plans completed: 1
- Average duration: 5 min
- Total execution time: 5 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-client-api-key-auth | 1 | 5 min | 5 min |

**Recent Trend:**
- Last 5 plans: 5 min
- Trend: baseline

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

### Pending Todos

- Plan 02: Implement ApiKeyService with HMAC-SHA256 key generation; replace stubs in ClientService
- Plan 03: Add ApiKeyAuthenticationFilter and second SecurityFilterChain for API key auth

### Blockers/Concerns

- DB state after running Plan 01 alone contains stub values (snd_stub_prefix / stub_hash) — not valid for authentication. Plans 01+02 must be run together before the system is usable.

## Session Continuity

Last session: 2026-03-10T13:36:44Z
Stopped at: Completed 01-01-PLAN.md
Resume file: None
