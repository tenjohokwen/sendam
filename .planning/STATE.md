# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-11)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 8 — Delivery Analytics (Admin)

## Current Position

Phase: 8 of 12 (Delivery Analytics — Admin)
Plan: 01 of 01 complete
Status: Phase complete
Last activity: 2026-03-11 — Completed 08-01-PLAN.md (delivery analytics admin)

Progress: v1.0 COMPLETE | v1.1 █░░░░ 20% (1/5 phases)

## Accumulated Context

### Decisions

All v1.0 decisions are logged in PROJECT.md Key Decisions table.

Phase 8 decisions:
- Repository<Object, Long> (not JpaRepository) for pure-aggregation repositories — no entity binding needed
- getSegmentTotals reuses findDeliveryStats — same aggregate row covers both endpoints, no duplicate method
- java.sql.Date return type on projection getDay() for DATE_TRUNC results — convert to LocalDate in service layer
- delivery_rate guard at total_sent==0 — explicit guard, returns 0.0 (no division-by-zero)

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

Last session: 2026-03-11T18:38:19Z
Stopped at: Completed 08-01-PLAN.md (delivery analytics admin — phase 8 complete)
Resume file: None
