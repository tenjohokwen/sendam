# Requirements: Sendam

**Defined:** 2026-03-10
**Core Value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.

## v1 Requirements

### Authentication (AUTH)

- [ ] **AUTH-01**: Client can authenticate API requests using a Bearer API key
- [ ] **AUTH-02**: Gateway derives client_id from the API key (clients must not send client_id)
- [ ] **AUTH-03**: Every API response includes an X-Request-ID trace header
- [ ] **AUTH-04**: API key authentication fails immediately when a key is revoked
- [ ] **AUTH-05**: Requests exceeding 10/sec or 1000 recipients/min per client are rejected with HTTP 429

### Credits (CREDIT)

- [x] **CREDIT-01**: Client can query current available credit balance (derived from ledger, p95 < 100ms)
- [x] **CREDIT-02**: Client can view full credit ledger history (paginated, all movement types)
- [x] **CREDIT-03**: Balance never goes negative — credit reservation is atomic and prevents overspend

### Top-Ups (TOPUP)

- [x] **TOPUP-01**: Client can submit a top-up request (amount, transaction_id, payment_type); status is PENDING_APPROVAL
- [x] **TOPUP-02**: Client can query the status of a top-up request by topup_id
- [x] **TOPUP-03**: Top-up transaction_id must be unique per client

### Send SMS (SMS)

- [x] **SMS-01**: Client can send SMS to one or more recipients in a single request
- [x] **SMS-02**: If any recipient is invalid or balance is insufficient, the entire request is rejected (no partial sends)
- [x] **SMS-03**: Credits are reserved atomically at request time before provider submission
- [x] **SMS-04**: Client can schedule an SMS for future delivery using a future UTC scheduleTime
- [x] **SMS-05**: Client can cancel a scheduled SMS before provider submission; reserved credits are released
- [x] **SMS-06**: Send requests are idempotent via sendRequestId — duplicate returns original response, no re-charge
- [ ] **SMS-07**: Credits are debited using provider-reported segment count, not estimated count

### Message Status (STATUS)

- [ ] **STATUS-01**: Client can query delivery status for a send request by sendRequestId (paginated per-recipient)
- [ ] **STATUS-02**: Messages progress through states: ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED
- [ ] **STATUS-03**: Message status data is purged 30 days after finalization; queries return 404 after window

### Webhooks (WEBHOOK)

- [x] **WEBHOOK-01**: Client can register a webhook URL and event subscription
- [x] **WEBHOOK-02**: Gateway delivers sms.finalized events to registered webhooks on message finalization
- [x] **WEBHOOK-03**: Gateway retries webhook delivery when the client endpoint is unreachable

### API Key Management (APIKEY)

- [ ] **APIKEY-01**: Client can create a new API key; raw value is shown exactly once, stored hashed
- [ ] **APIKEY-02**: Client can list all their API keys (id, created_at, status — no raw value)
- [ ] **APIKEY-03**: Client can revoke an API key; revoked keys immediately lose access

### Provider Integration (PROVIDER)

- [ ] **PROVIDER-01**: Gateway forwards validated SMS requests to the Nexah upstream provider
- [ ] **PROVIDER-02**: Gateway ingests Nexah delivery report callbacks and advances the message state machine
- [ ] **PROVIDER-03**: When Nexah is unavailable, new send requests are rejected with PROVIDER_UNAVAILABLE; credits unchanged

### Admin API (ADMIN)

- [ ] **ADMIN-01**: Admin can create a new client account and issue the first API key
- [x] **ADMIN-02**: Admin can approve or reject pending top-up requests
- [x] **ADMIN-03**: Admin can view all clients and their current credit balance

## v2 Requirements

(None — full v8 contract is in scope for v1.0)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Self-service client registration | Admin creates accounts; reduces fraud surface in v1 |
| Multi-provider support | Nexah only; no abstraction until second provider needed |
| Frontend / dashboard UI | API only in v1 |
| International phone numbers | Cameroon only (CamMobileValidator) |
| Webhook event types beyond sms.finalized | Only delivery events needed in v1 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Pending |
| AUTH-02 | Phase 1 | Pending |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Pending |
| AUTH-05 | Phase 1 | Pending |
| APIKEY-01 | Phase 1 | Pending |
| APIKEY-02 | Phase 1 | Pending |
| APIKEY-03 | Phase 1 | Pending |
| ADMIN-01 | Phase 1 | Pending |
| CREDIT-01 | Phase 2 | Complete |
| CREDIT-02 | Phase 2 | Complete |
| CREDIT-03 | Phase 2 | Complete |
| TOPUP-01 | Phase 2 | Complete |
| TOPUP-02 | Phase 2 | Complete |
| TOPUP-03 | Phase 2 | Complete |
| ADMIN-02 | Phase 2 | Complete |
| ADMIN-03 | Phase 2 | Complete |
| SMS-01 | Phase 3 | Complete |
| SMS-02 | Phase 3 | Complete |
| SMS-03 | Phase 3 | Complete |
| SMS-04 | Phase 3 | Complete |
| SMS-05 | Phase 3 | Complete |
| SMS-06 | Phase 3 | Complete |
| PROVIDER-01 | Phase 4 | Pending |
| PROVIDER-02 | Phase 4 | Pending |
| PROVIDER-03 | Phase 4 | Pending |
| SMS-07 | Phase 4 | Pending |
| STATUS-01 | Phase 4 | Pending |
| STATUS-02 | Phase 4 | Pending |
| STATUS-03 | Phase 4 | Pending |
| WEBHOOK-01 | Phase 5 | Complete |
| WEBHOOK-02 | Phase 5 | Complete |
| WEBHOOK-03 | Phase 5 | Complete |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-10*
*Last updated: 2026-03-10 after roadmap created*
