# Roadmap: Sendam

## Overview

Build a production SMS Gateway from the existing security/email/common foundation. Five phases proceed in dependency order: establish client identity and API key authentication first, then the financial ledger that governs credit flow, then the send-SMS core, then provider integration that closes the delivery loop and settles billing, and finally webhooks that push events to clients. Every v1 requirement maps to exactly one phase.

## Phases

- [x] **Phase 1: Client & API Key Authentication** — Client domain, API key issuance/auth, admin client creation, rate limiting
- [x] **Phase 2: Credit Ledger & Top-Ups** — Ledger-first balance model, top-up workflow, admin approval, atomic reservation guarantee
- [x] **Phase 3: Send SMS & Credit Reservation** — Single/bulk/scheduled send, all-or-nothing validation, idempotency, credit reservation
- [x] **Phase 4: Provider Integration & Message Status** — Nexah submission, DR callback ingestion, state machine, provider-confirmed billing, status query
- [x] **Phase 5: Webhooks** — Register URLs, deliver sms.finalized events, retry on failure
- [ ] **Phase 6: Fix API Key Security Chain** — Unblock APIKEY-01/02/03 by adding /v1/api/** to ClientSecurityConfiguration, remove dead TOPUPS_API constant
- [ ] **Phase 7: Fix Sender ID Forwarding** — Pass client-specified sender ID to Nexah instead of hardcoded global account sender

## Phase Details

### Phase 1: Client & API Key Authentication
**Goal**: Establish client identity, API key authentication, and rate limiting — everything else depends on knowing who the caller is.
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, APIKEY-01, APIKEY-02, APIKEY-03, ADMIN-01
**Success Criteria** (what must be TRUE):
  1. Admin can create a client account and the first API key; raw key value is shown once
  2. API requests authenticated via Bearer key are accepted; requests with invalid or revoked keys are rejected (403)
  3. Gateway derives client_id from the API key — clients do not send it
  4. Every API response carries an X-Request-ID trace header
  5. Clients exceeding 10 req/s or 1000 recipients/min receive HTTP 429
**Plans**: TBD

Plans:
- [ ] 01-01: Client entity, Flyway schema, admin create-client endpoint
- [ ] 01-02: API key generation (hashed storage), bearer token filter, client_id extraction
- [ ] 01-03: API key self-service (create/list/revoke), revocation enforcement, X-Request-ID filter, rate limiting

### Phase 2: Credit Ledger & Top-Ups
**Goal**: Implement the financial core — ledger-first balance model, top-up workflow, and the atomic reservation guarantee that ensures balance never goes negative.
**Depends on**: Phase 1
**Requirements**: CREDIT-01, CREDIT-02, CREDIT-03, TOPUP-01, TOPUP-02, TOPUP-03, ADMIN-02, ADMIN-03
**Success Criteria** (what must be TRUE):
  1. Client can query their current credit balance; balance is derived from the ledger (p95 < 100ms)
  2. Client can view their full paginated ledger history (TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND)
  3. Client can submit a top-up request and track its status; top-ups start as PENDING_APPROVAL
  4. Admin can approve or reject top-up requests; approval immediately credits the balance
  5. Concurrent send requests cannot drive balance negative — reservation is atomic
**Plans**: TBD

Plans:
- [x] 02-01: Ledger entity/repo, balance query (ledger-derived), ledger history endpoint
- [x] 02-02: Top-up request/status endpoints, admin approve/reject endpoints, admin view-clients endpoint
- [x] 02-03: Atomic credit reservation implementation (pessimistic lock or optimistic with retry)

### Phase 3: Send SMS & Credit Reservation
**Goal**: Clients can send single, bulk, and scheduled SMS messages with atomic credit reservation, all-or-nothing validation, and idempotency.
**Depends on**: Phase 2
**Requirements**: SMS-01, SMS-02, SMS-03, SMS-04, SMS-05, SMS-06
**Success Criteria** (what must be TRUE):
  1. Client can send SMS to one or more Cameroon recipients in a single request
  2. Requests with any invalid recipient or insufficient balance are rejected entirely — no partial sends
  3. Credits are reserved atomically when the request is accepted; balance reflects reservation immediately
  4. Client can schedule an SMS for future delivery; credits reserved at submission time
  5. Client can cancel a scheduled SMS before it is submitted; reserved credits are fully released
  6. Duplicate sendRequestId returns the original response without re-sending or re-charging
**Plans**: TBD

Plans:
- [ ] 03-01: SendRequest entity, sendRequestId idempotency (UNIQUE constraint + lookup), CamMobileValidator integration, sender ID validation
- [ ] 03-02: Credit reservation logic, balance deduction, all-or-nothing send validation
- [ ] 03-03: Scheduled SMS (scheduleTime field, scheduler job), cancel endpoint with credit release

### Phase 4: Provider Integration & Message Status
**Goal**: Close the delivery loop — submit messages to Nexah, ingest delivery reports, advance the state machine, settle billing on provider-confirmed segment counts, and expose status queries to clients.
**Depends on**: Phase 3
**Requirements**: PROVIDER-01, PROVIDER-02, PROVIDER-03, SMS-07, STATUS-01, STATUS-02, STATUS-03
**Success Criteria** (what must be TRUE):
  1. Accepted messages are submitted to Nexah; each gets a gateway_message_id and moves to SUBMITTED state
  2. Nexah delivery reports are ingested; messages advance to FINALIZED or FAIL_FINALIZED
  3. Billing is settled on provider-confirmed segment count; over-reservation generates an SMS_REFUND ledger entry
  4. When Nexah is unavailable, new send requests are rejected with PROVIDER_UNAVAILABLE and no credits are touched
  5. Client can query per-recipient delivery status by sendRequestId with pagination
  6. Message status records are purged 30 days after finalization; queries return 404 after the window
**Plans**: TBD

Plans:
- [x] 04-01: Nexah HTTP client (send SMS, DR callback endpoint), provider availability check, downtime handling
- [x] 04-02: State machine transitions (ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED), segment count settlement, SMS_REFUND ledger entry
- [x] 04-03: Message status query endpoint (paginated), 30-day purge job

### Phase 5: Webhooks
**Goal**: Clients can register webhook URLs to receive push notifications when messages are finalized, with retry on failure.
**Depends on**: Phase 4
**Requirements**: WEBHOOK-01, WEBHOOK-02, WEBHOOK-03
**Success Criteria** (what must be TRUE):
  1. Client can register a webhook URL and receive a webhook_id
  2. sms.finalized events are delivered to registered webhook URLs when messages reach FINALIZED or FAIL_FINALIZED
  3. Failed webhook deliveries are retried with backoff until the endpoint acknowledges
**Plans**: TBD

Plans:
- [x] 05-01: Webhook registration endpoint, Webhook entity/repo
- [x] 05-02: Webhook delivery on message finalization, retry-with-backoff mechanism

### Phase 6: Fix API Key Security Chain
**Goal**: Unblock APIKEY-01, APIKEY-02, APIKEY-03 — the three API key self-service endpoints are unreachable because `/v1/api/**` is missing from the API key security filter chain.
**Depends on**: Phase 1 (gap closure)
**Requirements**: APIKEY-01, APIKEY-02, APIKEY-03
**Gap Closure**: Closes CRITICAL-1 and CRITICAL-2 from v1.0 milestone audit
**Success Criteria** (what must be TRUE):
  1. `GET /v1/api/keys` returns the client's API keys when authenticated with a valid API key
  2. `POST /v1/api/keys` creates a new API key and shows raw value once
  3. `DELETE /v1/api/keys/{id}` revokes a key; revoked key immediately loses access
  4. Dead `TOPUPS_API` constant removed from `AppEndpoints` and `ClientSecurityConfiguration`

Plans:
- [ ] 06-01: Add CLIENT_API_KEYS constant to AppEndpoints, include in ClientSecurityConfiguration matcher, remove dead TOPUPS_API

### Phase 7: Fix Sender ID Forwarding
**Goal**: Pass the client-specified sender ID to Nexah instead of the hardcoded global account sender.
**Depends on**: Phase 4 (gap closure)
**Requirements**: SMS-01 (correctness gap — sender field accepted but ignored)
**Gap Closure**: Closes WIRING-1 from v1.0 milestone audit
**Success Criteria** (what must be TRUE):
  1. When a client sends SMS with `"sender": "MYAPP"`, Nexah receives `"from": "MYAPP"` (not the global account sender)
  2. Existing behavior is preserved when sender equals the global default

Plans:
- [ ] 07-01: Replace nexahProperties.senderid() with request.getSender() in NexahDispatchService.dispatch()

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Client & API Key Auth | 3/3 | Complete | 2026-03-10 |
| 2. Credit Ledger & Top-Ups | 3/3 | Complete | 2026-03-10 |
| 3. Send SMS | 4/4 | Complete | 2026-03-10 |
| 4. Provider Integration | 3/3 | Complete | 2026-03-10 |
| 5. Webhooks | 2/2 | Complete | 2026-03-11 |
| 6. Fix API Key Security Chain | 0/1 | Not started | - |
| 7. Fix Sender ID Forwarding | 0/1 | Not started | - |
