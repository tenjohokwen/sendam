# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 5 — Webhooks (in progress)

## Current Position

Phase: 5 of 5 (Webhooks)
Plan: 02 of 2
Status: COMPLETE — all phases done
Last activity: 2026-03-11 — Completed 05-02-PLAN.md (webhook delivery: SmsFinalisedEvent, SmsFinalisedListener, dispatchPendingDeliveries, exponential backoff)

Progress: █████████████████████ (10 of ~14 plans complete, Phase 5 complete, v1.0 milestone DONE)

## Performance Metrics

**Velocity:**
- Total plans completed: 10
- Average duration: 6 min
- Total execution time: 58 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-client-api-key-auth | 3 | 15 min | 5 min |
| 02-credit-ledger-topups | 2 | 16 min | 8 min |
| 03-send-sms | 4 | 26 min | 7 min |
| 04-provider-integration | 3 | 14 min | 5 min |
| 05-webhooks | 2 | 15 min | 7.5 min |

**Recent Trend:**
- Last 5 plans: 4 min, 6 min, 6 min, 2 min, 7 min
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
- Recipient rate limit now throws RateLimitExceededException (extends ApplicationException, NOT AuthorizationException) → HTTP 429; AuthorizationException -> 401 path unchanged
- RateLimitExceededException must extend ApplicationException directly (not AuthorizationException) — inheriting AuthorizationException would inherit the 401 handler; sibling hierarchy enables independent 429 mapping
- SmsValidationException carries SmsError as constructor parameter — single class handles INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME with distinct error_code values in responses
- Three new ApiAdvice handlers: SmsValidationException -> 400, CancelNotAllowedException -> 409, RateLimitExceededException -> 429 (all after topupAlreadyProcessedHandler)
- BalanceResponse.availableBalance() is the correct record accessor (not .balance()) — maps to @JsonProperty("available_balance")
- getStatus pageSize clamped to 200 — matches CreditService.getLedgerHistory convention
- fixedDelay (not fixedRate) on SmsSchedulerService — prevents overlapping scheduled dispatch runs
- SmsSchedulerService does NOT call CreditReservationService — scheduler marks SUBMITTED only; Phase 4 wires actual debit after Nexah confirmation
- Cancel guard requires status=ACCEPTED AND scheduleTime IS NOT NULL — immediate sends cannot be cancelled
- DELETE /v1/sms/scheduled/{id} not rate-limited per v8 contract
- NexahClient does NOT extend AbstractClient — AbstractClient requires RestRequestInterceptor for MoMo auth; Nexah uses credential-in-body POST auth, incompatible with interceptor model
- ProviderError enum created for PROVIDER_UNAVAILABLE — consistent with SmsError/ClientError pattern (each domain defines its own ErrorCode enum)
- NexahSecurityConfiguration @Order(0) with securityMatcher('/v1/provider/**') — scoped narrowly so @Order(1) API key chain still guards /v1/sms/**, /v1/credits/**
- DrCallbackResource is a deliberate stub in Plan 04-01 — Plan 04-02 wires DrCallbackService
- checkAvailability() in NexahClient not wrapped by circuit breaker — it IS the probe, not the guarded operation
- NexahDispatchService matches recipients by mobileNo (not array index) — safe for partial/reordered Nexah responses; unmatched stay ACCEPTED for stale recovery
- finalizeParentIfAllTerminal extracted as shared helper called by processDr and forceFinalizeStaleSms — single debit() path, no duplication
- Debit cap: Math.min(totalActualSegments, reservedCredits) — prevents IllegalArgumentException when actual > reserved; zero fallback uses reservedCredits conservatively
- DR idempotency: TERMINAL_STATUSES EnumSet guards duplicate DR processing — status=1 returned without re-calling debit()
- SmsService checks CircuitBreaker OPEN || HALF_OPEN before reserve() — no ledger entry created during provider outage (in-memory check, no network call)
- findFinalizedBefore returns List<Long> not List<SendRequest> — IDs sufficient for delete; avoids loading full entity graphs for large purge sets
- Batch size 500 for SmsPurgeService — prevents unbounded IN-clause, acceptable trade-off for background job
- SmsPurgeService does not add @EnableScheduling — already present in ClientConfig
- attempt_status column (PENDING/DELIVERED/FAILED/EXHAUSTED) used in webhook_delivery instead of status — avoids collision with AbstractAuditingEntity inherited status column (EntityStatus); matches send_status convention from Phase 3
- @EnableRetry placed in WebhookConfig, not ClientConfig — retry infrastructure co-located with webhookRestTemplate
- WebhookEndpointRepository.findByClientIdAndStatus uses EntityStatus not WebhookStatus — inherited status column carries entity lifecycle; WebhookStatus is contract-layer only
- wh_ prefix + UUID substring for public webhook IDs — avoids exposing BIGINT TSID as webhook_id
- @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) always paired — AFTER_COMMIT leaves no ambient TX; REQUIRES_NEW opens a fresh one for listener writes
- Explicit constructor over @RequiredArgsConstructor when @Qualifier needed — Lombok cannot propagate @Qualifier to generated constructor parameters
- SmsFinalisedListener uses EntityStatus.ACTIVE not WebhookStatus.ACTIVE — WebhookEndpointRepository.findByClientIdAndStatus takes EntityStatus (inherited lifecycle column)
- postWebhook() is public on WebhookService — required for Spring AOP proxy to intercept @Retryable; private methods bypass AOP proxy

### Pending Todos

- LockTimeoutException handler now COMPLETE — added to ApiAdvice in 03-01 (LockTimeoutException + CannotAcquireLockException both handled)
- AUTH-05 now COMPLETE — N-token tryConsume wired in SmsService.sendSms (03-02); @RateLimited(10/s) on both resource and service
- Phase 3 (SMS-01 through SMS-06) now COMPLETE — all six requirements satisfied + all HTTP status code gaps closed (03-04)
- Phase 4 (provider-integration) now COMPLETE — Nexah client, DR state machine, stale recovery, purge job all wired (04-01 through 04-03)
- Phase 5 (webhooks) now COMPLETE — WEBHOOK-01, WEBHOOK-02, WEBHOOK-03 all satisfied (05-01 through 05-02)
- v1.0 milestone COMPLETE — all phases done

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — the fallback default is documented as unsafe.

## Session Continuity

Last session: 2026-03-11T02:27:59Z
Stopped at: Completed 05-02-PLAN.md (webhook delivery: SmsFinalisedEvent published, SmsFinalisedListener, WebhookService poller + backoff)
Resume file: None
