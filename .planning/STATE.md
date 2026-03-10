# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 2 — Credit Ledger & Top-Ups

## Current Position

Phase: 3 of 5 (Send SMS)
Plan: 03 of 3
Status: Phase complete
Last activity: 2026-03-10 — Completed 03-03-PLAN.md (SmsSchedulerService + cancelScheduled + DELETE /v1/sms/scheduled/{id})

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
| 03-send-sms | 3 | 22 min | 7 min |

**Recent Trend:**
- Last 5 plans: 8 min, 8 min, 8 min, 6 min, 8 min
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
- SmsSegmentCalculator is package-private within client.service — only SmsService needs it; prevents accidental misuse from external packages
- AppEndpoints.CLIENT_API updated to /v1/** — ClientSecurityConfiguration uses the constant (not hardcoded string) as single source of truth for security matcher
- Both jakarta.persistence.LockTimeoutException and Spring's CannotAcquireLockException handled in ApiAdvice → 503 (defensive: uncertain which variant Hibernate throws with JPA lock timeout hint)
- SmsError does NOT duplicate INSUFFICIENT_CLIENT_BALANCE — ClientError.INSUFFICIENT_CLIENT_BALANCE is the canonical code; SmsError owns only SMS-specific codes
- send_status column (not status) used for SMS lifecycle in send_request and send_request_recipient — avoids Hibernate mapping collision with inherited status from AbstractAuditingEntity
- @RateLimited placed on SmsService.sendSms (service boundary) as well as SmsResource — authoritative enforcement regardless of caller
- Recipient rate limit throws AuthorizationException(TOO_MANY_REQUESTS) → HTTP 401, matching existing RateLimitingAspect pattern; plan's "429" notation reflects intent, not a new exception type
- BalanceResponse.availableBalance() is the correct record accessor (not .balance()) — maps to @JsonProperty("available_balance")
- getStatus pageSize clamped to 200 — matches CreditService.getLedgerHistory convention
- fixedDelay (not fixedRate) on SmsSchedulerService — prevents overlapping scheduled dispatch runs
- SmsSchedulerService does NOT call CreditReservationService — scheduler marks SUBMITTED only; Phase 4 wires actual debit after Nexah confirmation
- Cancel guard requires status=ACCEPTED AND scheduleTime IS NOT NULL — immediate sends cannot be cancelled
- DELETE /v1/sms/scheduled/{id} not rate-limited per v8 contract

### Pending Todos

- LockTimeoutException handler now COMPLETE — added to ApiAdvice in 03-01 (LockTimeoutException + CannotAcquireLockException both handled)
- AUTH-05 now COMPLETE — N-token tryConsume wired in SmsService.sendSms (03-02); @RateLimited(10/s) on both resource and service
- Phase 3 (SMS-01 through SMS-06) now COMPLETE — all six requirements satisfied after 03-03

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — the fallback default is documented as unsafe.

## Session Continuity

Last session: 2026-03-10T19:43:45Z
Stopped at: Completed 03-03-PLAN.md (SmsSchedulerService + cancelScheduled + DELETE /v1/sms/scheduled/{id}) — Phase 3 complete
Resume file: None
