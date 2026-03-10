# Sendam

## What This Is

Sendam is a multi-tenant SMS Gateway platform that allows clients to send single, bulk, and scheduled SMS messages through the Nexah upstream provider. It provides strong financial correctness through an auditable credit ledger, idempotent request handling, and delivery tracking with webhook notifications. Clients interact via a REST API; administrators manage accounts and approve top-ups via a separate admin API.

## Core Value

Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward these. -->

**Client-facing API (v8 contract):**
- [ ] API key authentication — clients authenticate via Bearer token; gateway derives client_id from key
- [ ] Credit balance query — `GET /v1/credits/balance`, p95 < 100ms
- [ ] Credit ledger history — `GET /v1/credits/ledger` with full audit trail
- [ ] Top-up request — `POST /v1/credits/topups` (PENDING_APPROVAL state until admin approves)
- [ ] Top-up status query — `GET /v1/credits/topups/{topup_id}`
- [ ] Send SMS (single, bulk, scheduled) — `POST /v1/sms/send` with credit reservation and idempotency
- [ ] Cancel scheduled SMS — `DELETE /v1/sms/scheduled/{sendRequestId}`
- [ ] Message status query — `GET /v1/sms/status/{sendRequestId}` with pagination
- [ ] Webhook registration — `POST /v1/webhooks` to receive delivery events
- [ ] API key management — create, list, revoke keys (`/v1/api-keys`)
- [ ] Rate limiting — 10 req/s and 1000 recipients/min per client

**Admin API:**
- [ ] Create client account (admin issues first API key)
- [ ] Approve / reject top-up requests
- [ ] View all clients and their balances

**Provider integration:**
- [ ] Nexah upstream integration (send SMS, receive delivery reports / DRs)
- [ ] Provider downtime handling with retry policy

**Financial guarantees:**
- [ ] Balance never goes negative (atomic reservation)
- [ ] Billing uses provider-confirmed segment count, not estimated
- [ ] Ledger is the single source of truth for all credit movements

### Out of Scope

- Self-service client registration — admin creates accounts; no public signup in this milestone
- Multi-provider support — only Nexah for v1.0
- Dashboard / frontend UI — API only
- International phone numbers — only Cameroon (E.164, validated via `CamMobileValidator`)

## Context

- **Tech stack**: Spring Boot 3.5.11, Java 17, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway, Spring Cloud
- **Upstream provider**: Nexah BulkSMS (`smsvas.com`), simple REST API (send SMS, get balance, receive DRs)
- **Existing foundation**: Security module (JWT auth, users, 2FA, audit), email module, common infra (payment, message, consumer, persistence, `CamMobileValidator`)
- **Architecture pattern**: Layered packages (`api → service → repo`, `infrastructure`, `contract`, `common`, `config`) with strict unidirectional imports — see `ARCHITECTURE.md`
- **Phone validation**: Cameroon numbers only; use existing `com.softropic.sendam.common.validation.CamMobileValidator`
- **Sender ID rules**: Max 11 chars, A-Z 0-9 only
- **SMS segment billing**: GSM-7 (160/153 chars), UCS-2 (70/67 chars); reserve estimated, settle on provider-confirmed
- **Message states**: ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED
- **Ledger entry types**: TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND
- **Data retention**: Message status 30 days; ledger indefinite

## Constraints

- **Tech stack**: Spring Boot + PostgreSQL — no new frameworks or databases
- **Phone numbers**: Cameroon only (E.164 with "237" prefix) — validated via existing `CamMobileValidator`
- **Partial sends**: Not allowed — if any recipient invalid or balance insufficient, entire request rejected
- **API key visibility**: Raw key value shown only at creation time; stored hashed
- **sendRequestId**: Unique per client forever (UNIQUE constraint on `client_id, sendRequestId`)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|------------|
| Ledger-first balance model | Balance derived from ledger entries, never stored as mutable field — prevents drift and makes all movements auditable | — Pending |
| Atomic credit reservation on send | Reserve credits synchronously at request time, not after provider confirmation — prevents overspending even under concurrent load | — Pending |
| Admin-only client onboarding | No self-registration reduces fraud surface and keeps v1.0 scope manageable | — Pending |
| Nexah only (no abstraction layer) | Single provider for v1.0; avoid premature abstraction. Add provider interface when second provider is needed | — Pending |

---
*Last updated: 2026-03-10 after Milestone v1.0 initialized*
