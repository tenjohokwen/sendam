# Milestones

## Active

(None — planning next milestone)

---

## Archived

### v1.1 — Operations & Observability (Shipped: 2026-03-12)

**Delivered:** Admin-facing operational visibility (delivery analytics, spend reporting, system health, audit log) and client-facing self-service analytics — all 17 v1.1 requirements satisfied.

**Phases completed:** 8–12 (7 plans total)

**Key accomplishments:**

- Admin delivery analytics — `GET /api/admin/analytics/delivery-stats` + `/segment-totals` with daily breakdown; closed-projection native SQL pattern on send_request_recipient
- Admin spend reporting — `GET /api/admin/spend/credits` (net consumption + per-type breakdown) + `/topups` (filterable top-up history); ABS() sign-convention for debit amounts
- Admin system health — circuit breaker state (live Resilience4j registry), webhook delivery aggregates, provider send stats; AppEndpoints migrated to Map.ofEntries() (11+ entries)
- Audit log — append-only audit_event table, REQUIRES_NEW write pipeline, exception-swallowing listener, 8 event hook points across 5 services; paginated admin query API
- Client analytics — three client-scoped endpoints secured by API-key chain; clientId from SecurityContextHolder only; no admin-only fields exposed; reuses existing service layer

**Stats:**

- 77 files changed, 6,069 insertions, 81 deletions
- ~21,329 LOC main Java, ~7,530 LOC test Java
- 5 phases, 7 plans
- 1 day (2026-03-11 → 2026-03-12)

**Archive:** `.planning/milestones/v1.1-ROADMAP.md`

---

### v1.0 — SMS Gateway (Shipped: 2026-03-11)

**Delivered:** Full REST API SMS gateway with API key auth, credit ledger, Nexah provider integration, delivery report state machine, and webhook notifications — all 33 v1 requirements satisfied.

**Phases completed:** 1-7 (16 plans total)

**Key accomplishments:**

- Multi-tenant client identity with HMAC-SHA256 hashed API key auth, dual SecurityFilterChain @Order(1)/@Order(2), X-Request-ID trace header, and 10 req/s / 1000 recipients-per-minute rate limiting
- Ledger-first credit model with atomic reservation (SELECT FOR UPDATE) — balance never goes negative under concurrent load; full audit trail via TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND entry types
- Full SMS send pipeline — single/bulk/scheduled sends, all-or-nothing CamMobileValidator validation, sendRequestId idempotency, scheduled cancel with credit release
- Nexah provider integration with Resilience4j circuit breaker, delivery report state machine (ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED), provider-confirmed segment billing with SMS_REFUND for over-reservation, 30-day purge
- Webhook delivery system — sms.finalized events, AFTER_COMMIT transactional listeners, @Retryable with exponential backoff (1m → 5m → 30m → 2h → EXHAUSTED)
- Post-audit gap closures: API key security chain (CRITICAL-1), ClientApiKeyEntity field-shadowing bug, sender ID forwarding to Nexah (WIRING-1)

**Stats:**

- 170 files changed, 17,209 insertions
- ~20,200 LOC main Java, ~7,500 LOC test Java (~27,700 total)
- 7 phases, 16 plans
- 2 days (2026-03-10 → 2026-03-11)

**Archive:** `.planning/milestones/v1.0-ROADMAP.md`

---
