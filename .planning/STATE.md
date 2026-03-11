# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-11)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** v1.1 — Operations & Observability

## Current Position

Phase: Not started (run /gsd:create-roadmap)
Plan: —
Status: Defining requirements
Last activity: 2026-03-11 — Milestone v1.1 started

Progress: v1.0 COMPLETE (16/16 plans, 7/7 phases) | v1.1 NOT STARTED

## Accumulated Context

### Decisions

All v1.0 decisions are logged in PROJECT.md Key Decisions table.

Key architectural invariants for future milestones:

- Ledger-first balance: balance derived from ledger entries, never a mutable column
- Atomic reservation: credits reserved synchronously at send time via SELECT FOR UPDATE
- Child entities must NOT re-declare 'status' field — Hibernate field-access on parent @Column; shadowing breaks hydration silently
- ApiKeyAuthenticationFilter not @Component — instantiated manually in ClientSecurityConfiguration
- @Order(1)/@Order(2) dual SecurityFilterChain — required for /v1/api/** and /v1/** coexistence
- send_status / attempt_status columns (not status) for domain lifecycle — avoids AbstractAuditingEntity collision
- @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) always paired for post-commit event writes
- APIKEY_PEPPER env var must be set in production; fallback 'change-me-in-production' is unsafe

### Pending Todos

(None — clean slate for v1.1)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — fallback default is documented as unsafe
- Non-blocking tech debt from v1.0:
  - Wrong error code for blank-message validation (INVALID_SENDER_ID used instead of message-specific code)
  - Duplicate @RateLimited on SmsResource AND SmsService fires AOP aspect twice per request (wastes rate-limit tokens)
  - TODO comments in AppEndpoints.java and SecurityConfiguration.java

## Session Continuity

Last session: 2026-03-11
Stopped at: v1.0 milestone archived
Resume file: None
