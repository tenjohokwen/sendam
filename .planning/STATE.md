# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 2 — Credit Ledger & Top-Ups

## Current Position

Phase: 2 of 5 (Credit Ledger & Top-Ups)
Plan: 03 of 3 (Phase 2 complete)
Status: Phase complete
Last activity: 2026-03-10 — Completed 02-03-PLAN.md (CreditReservationService + 7-test unit suite)

Progress: ██████████ 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: 6 min
- Total execution time: 31 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-client-api-key-auth | 3 | 15 min | 5 min |
| 02-credit-ledger-topups | 2 | 16 min | 8 min |

**Recent Trend:**
- Last 5 plans: 5 min, 4 min, 6 min, 8 min, 8 min
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
- RequestIdResponseFilter registered globally via FilterRegistrationBean (not @Component) at HIGHEST_PRECEDENCE + 1
- Refill.greedy for sub-60s windows in RateLimitingService — prevents burst-at-boundary for 1s rate limit
- revokeKey uses identical ResourceNotFoundException message for missing vs cross-client keys — obscures ownership
- N-token tryConsume overload added to RateLimitingService in Phase 1 as infrastructure; enforcement in Phase 3 SMS send endpoint
- ClientError enum owns INSUFFICIENT_CLIENT_BALANCE error code — each domain defines its own ErrorCode enum (SecError, ResourceError, ClientError)
- TOPUP_PENDING entries (amount=0) pass through applyLedgerEntry without modifying balance — audit trail only; balance update happens on TOPUP_APPROVED
- size clamped to max 200 in CreditService.getLedgerHistory — prevents unbounded page size requests
- CreditService.applyLedgerEntry is the single canonical write path for all credit mutations — TopupService and CreditReservationService must use this method, never write directly to ledger or balance tables
- CreditReservationService manages its own lock acquisition independently — not delegating to CreditService.applyLedgerEntry() avoids implicit dependency on call ordering within a single transaction
- reserve() returns the ledger entry id as reservationId — Phase 3 passes this back to debit()/release() to load the original reservation and derive the reserved amount
- debit() does NOT subtract actualAmount from balance again — balance was already reduced by reserve(); debit only adjusts the balance upward for over-reservation
- release() and debit() both re-acquire findByClientIdForUpdate — each method is independently correct, not relying on a prior lock still being held

### Pending Todos

- Phase 3: Wire N-token tryConsume overload in SMS send endpoint for 1000 recipients/min AUTH-05 enforcement
- Phase 3: Add @ExceptionHandler in ApiAdvice for LockTimeoutException → HTTP 503 (needed when SMS send endpoint exists)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — the fallback default is documented as unsafe.
- AUTH-05 (1000 recipients/min) is NOT complete — N-token bucket infrastructure in place, enforcement wiring deferred to Phase 3 SMS send endpoint.

## Session Continuity

Last session: 2026-03-10T15:46:55Z
Stopped at: Completed 02-03-PLAN.md (CreditReservationService + unit tests — Phase 2 complete)
Resume file: None
