# Milestones

## Active

(None — planning next milestone)

---

## Archived

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
