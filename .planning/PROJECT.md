# Sendam

## What This Is

Sendam is a multi-tenant SMS Gateway platform that allows clients to send single, bulk, and scheduled SMS messages through the Nexah upstream provider. It provides strong financial correctness through an auditable credit ledger, idempotent request handling, provider-confirmed segment billing, delivery state tracking, and webhook push notifications. Clients interact via a REST API; administrators manage accounts and approve top-ups via a separate admin API.

v1.0 shipped 2026-03-11: all 33 v1 requirements satisfied, 7 phases, 16 plans, ~27,700 LOC Java.

## Core Value

Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.

## Requirements

### Validated

<!-- Shipped and confirmed. Format: ✓ requirement — v[X.Y] -->

- ✓ API key authentication (Bearer token, HMAC-SHA256 hashed, client_id derived from key) — v1.0
- ✓ X-Request-ID trace header on all responses — v1.0
- ✓ API key revocation (immediate effect) — v1.0
- ✓ Rate limiting (10 req/s, 1000 recipients/min) — v1.0
- ✓ API key self-service (create/list/revoke) — v1.0
- ✓ Admin creates client account, issues first API key — v1.0
- ✓ Credit balance query (ledger-derived, p95 < 100ms) — v1.0
- ✓ Credit ledger history (paginated, all movement types) — v1.0
- ✓ Atomic credit reservation (balance never goes negative) — v1.0
- ✓ Top-up request/approval workflow — v1.0
- ✓ Admin view of all clients and balances — v1.0
- ✓ Send SMS (single/bulk/scheduled) with all-or-nothing validation — v1.0
- ✓ Credit reservation at send time, settle on provider-confirmed segment count — v1.0
- ✓ Schedule SMS for future delivery; cancel with credit release — v1.0
- ✓ sendRequestId idempotency (no re-send, no re-charge) — v1.0
- ✓ Nexah provider integration (send, DR callback, circuit breaker) — v1.0
- ✓ Delivery state machine (ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED) — v1.0
- ✓ Provider-unavailable guard (no credits touched when circuit open) — v1.0
- ✓ Per-recipient delivery status query (paginated) — v1.0
- ✓ 30-day message purge (status 404 after window) — v1.0
- ✓ Webhook registration (URL + event subscription) — v1.0
- ✓ sms.finalized event delivery on message finalization — v1.0
- ✓ Webhook retry with exponential backoff — v1.0
- ✓ Client-specified sender ID forwarded to Nexah — v1.0
- ✓ Admin delivery analytics (sent/delivered/failed, delivery rate, segment totals, daily breakdown) — v1.1
- ✓ Admin spend reporting (net credits consumed with per-type breakdown, top-up history) — v1.1
- ✓ Admin system health (Nexah circuit breaker state, webhook delivery stats, provider send stats) — v1.1
- ✓ Audit log (admin actions, API key ops, SMS submissions, webhook config changes, paginated query) — v1.1
- ✓ Client delivery analytics (own delivery stats, segment totals, credit consumption via API-key auth) — v1.1

### Active

<!-- Current scope for v1.3. Building toward these. -->

**Milestone: v1.3 — Provider Integrity & Platform Credit Account**

*Goal: Introduce a platform-level credit account so client credits are always backed by real Nexah credits, and detect billing deviations between Sendam's segment calculations and Nexah's reported consumption.*

**Platform credit account:**
- [ ] Single platform balance entity tracking credits Sendam purchased from Nexah
- [ ] Admin records Nexah credit purchases (increases platform balance with ledger entry)
- [ ] Client top-up approval debits platform balance; rejected if platform balance would go negative
- [ ] Admin can query platform balance and its ledger history
- [ ] Platform balance initialised at zero; must be topped up before any client top-ups can be approved

**Per-send segment deviation tracking:**
- [ ] Before each send, Sendam calculates expected segments per recipient (same formula used for credit reservation)
- [ ] After Nexah responds, compare Sendam's expected `total_sms_unit` vs Nexah's reported `total_sms_unit` per recipient
- [ ] On mismatch: record a `SEGMENT` deviation alert (sendRequestId, recipient, expected, actual, delta, timestamp) with status `OPEN`

**Periodic platform balance reconciliation:**
- [ ] Scheduled job polls Nexah `/smscredit` on a fixed interval (configurable, default 15 min)
- [ ] Compare Nexah's reported balance vs Sendam's internally tracked platform balance
- [ ] On mismatch: record a `BALANCE` deviation alert (expected, actual, delta, timestamp) with status `OPEN`

**Admin deviation management:**
- [ ] `GET /api/admin/deviations` — paginated list, filterable by type (`SEGMENT`/`BALANCE`) and status (`OPEN`/`ACKNOWLEDGED`/`RESOLVED`)
- [ ] `PUT /api/admin/deviations/{id}/acknowledge` — admin adds a note; status → `ACKNOWLEDGED`
- [ ] `PUT /api/admin/deviations/{id}/resolve` — admin adds a resolution note; status → `RESOLVED`
- [ ] Each deviation record retains full history: timestamps, delta, related entity (sendRequestId or balance snapshot), admin notes

### Out of Scope

- Self-service client registration — admin creates accounts; no public signup
- Multi-provider support — only Nexah; no abstraction until second provider needed
- Dashboard / frontend UI — API only
- International phone numbers — only Cameroon (E.164, validated via `CamMobileValidator`)
- Webhook event types beyond sms.finalized — only delivery events in v1

## Context

- **Tech stack**: Spring Boot 3.5.11, Java 17, Spring Security (JWT + API key dual chain), Spring Data JPA, PostgreSQL, Flyway, Spring Cloud (Resilience4j circuit breaker, Spring Retry)
- **Upstream provider**: Nexah BulkSMS (`smsvas.com`), REST API (send SMS, receive DRs via callback)
- **Existing foundation**: Security module (JWT auth, users, 2FA, audit), email module, common infra (payment, message, consumer, persistence, `CamMobileValidator`)
- **Architecture pattern**: Layered packages (`api → service → repo`, `infrastructure`, `contract`, `common`, `config`) — see `ARCHITECTURE.md`
- **Current codebase**: ~21,329 LOC main Java, ~7,530 LOC test Java; 156 passing tests
- **Shipped**: v1.0 on 2026-03-11, v1.1 on 2026-03-12

## Constraints

- **Tech stack**: Spring Boot + PostgreSQL — no new frameworks or databases
- **Phone numbers**: Cameroon only (E.164 with "237" prefix) — validated via existing `CamMobileValidator`
- **Partial sends**: Not allowed — if any recipient invalid or balance insufficient, entire request rejected
- **API key visibility**: Raw key value shown only at creation time; stored hashed (HMAC-SHA256)
- **sendRequestId**: Unique per client forever (UNIQUE constraint on `client_id, sendRequestId`)
- **APIKEY_PEPPER env var**: Must be set in production; fallback `change-me-in-production` is documented as unsafe

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Ledger-first balance model | Balance derived from ledger entries, never stored as mutable field — prevents drift and makes all movements auditable | ✓ Good — balance query is consistent and auditable |
| Atomic credit reservation on send | Reserve credits synchronously at request time, not after provider confirmation — prevents overspending even under concurrent load | ✓ Good — SELECT FOR UPDATE works reliably; no balance underflow in tests |
| Admin-only client onboarding | No self-registration reduces fraud surface and keeps v1.0 scope manageable | ✓ Good — appropriate for controlled rollout |
| Nexah only (no abstraction layer) | Single provider for v1.0; avoid premature abstraction | ✓ Good — NexahClient does NOT extend AbstractClient (incompatible auth model) |
| HMAC-SHA256 for API key hashing | Deterministic hash enables prefix lookup + constant-time comparison | ✓ Good — BCrypt not suitable for lookup-by-hash pattern |
| ApiKeyAuthenticationFilter not @Component | Prevents Spring Boot global servlet filter auto-registration | ✓ Good — instantiated manually in ClientSecurityConfiguration |
| @Order(1)/@Order(2) dual SecurityFilterChain | Explicit ordering for API key chain (@Order 1) vs JWT chain (@Order 2) | ✓ Good — without it, Integer.MAX_VALUE on both causes undefined behavior for /v1/api/** |
| debit() does NOT subtract again | reserve() already reduced balance; debit() only adjusts upward for over-reservation | ✓ Good — avoids double-deduction; reserve/debit/release trio is well-tested |
| @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) | AFTER_COMMIT leaves no ambient TX; REQUIRES_NEW opens fresh one for webhook writes | ✓ Good — essential pattern for event-driven writes post-commit |
| send_status column (not status) for SMS lifecycle | Avoids Hibernate mapping collision with AbstractAuditingEntity.status | ✓ Good — prevents field-access ambiguity; reused convention for attempt_status in webhooks |
| Child entities must NOT re-declare 'status' field | Hibernate field-access maps parent @Column; shadowing it breaks hydration silently | ✓ Good — caught and fixed as Phase 6 bug (ClientApiKeyEntity) |
| Repository<Object, Long> for pure-aggregation repos | No entity binding needed for native SQL analytics queries; avoids dummy entity requirement | ✓ Good — used in phases 8, 9, 10; clean and explicit |
| Map.ofEntries() mandatory for SECURED_MAPPINGS | Map.of() capped at 10 pairs; migrated at phase 10 when 11th entry needed | ✓ Good — no further migration needed; ofEntries() has no cap |
| AuditEventEntity extends BaseEntity only | Audit table is append-only; AbstractAuditingEntity adds unwanted status column | ✓ Good — table stays immutable; no lifecycle columns to maintain |
| AuditEventService.record() uses REQUIRES_NEW | Audit rows must persist even when outer TX rolls back | ✓ Good — mirrors TrailService pattern; audit is isolated from caller outcome |
| AuditEventType passed as param by callers | Single ApiKeyService handles both admin and client key ops; caller determines event type | ✓ Good — avoids duplicating key generation logic for different audit semantics |
| CLIENT_ANALYTICS excluded from SECURED_MAPPINGS | Client endpoints use @Order(1) API-key chain; adding to SECURED_MAPPINGS would require JWT/ADMIN role | ✓ Good — consistent with all other /v1/** client endpoints |

## Current Milestone: v1.3 — Provider Integrity & Platform Credit Account

**Goal:** Introduce a platform-level credit account so client credits are always backed by real Nexah credits, and detect + surface billing deviations between Sendam's segment calculations and Nexah's reported consumption — giving admins traceable evidence for reconciliation with Nexah.

**Scope:**
- Platform balance entity (single row) + ledger; admin records Nexah purchases
- Constrained top-up approval (platform balance debited; rejects if insufficient)
- Per-send segment deviation detection using Nexah's `total_sms_unit` in send responses
- Periodic platform balance reconciliation via Nexah `/smscredit` poll
- `deviation_alert` table with `OPEN → ACKNOWLEDGED → RESOLVED` lifecycle + admin notes
- Admin REST API for querying and actioning deviations

**Out of scope for v1.3:**
- Email/push notifications for deviations (admin polls the API)
- Automatic resolution of deviations
- Client-facing visibility of platform balance or deviations

---

## Previous Milestones

- **v1.0 — SMS Gateway** (shipped 2026-03-11) — 7 phases, 16 plans, 33 requirements. See `.planning/milestones/v1.0-ROADMAP.md`
- **v1.1 — Operations & Observability** (shipped 2026-03-12) — 5 phases, 7 plans, 17 requirements. See `.planning/milestones/v1.1-ROADMAP.md`

---
*Last updated: 2026-03-16 after v1.3 milestone started*
