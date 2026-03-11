# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-11)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 12 — Client Analytics

## Current Position

Phase: 12 of 12 (Client Analytics) — In progress
Plan: 1 of 1 — COMPLETE
Status: In progress
Last activity: 2026-03-11 — Completed 12-01-PLAN.md (client analytics endpoints: CANL-01/02/03 — ClientAnalyticsResource, 3 response DTOs, 3 service methods, AppEndpoints.CLIENT_ANALYTICS)

Progress: v1.0 COMPLETE | v1.1 █████ 100% (all 6 phases complete)

## Accumulated Context

### Decisions

All v1.0 decisions are logged in PROJECT.md Key Decisions table.

Phase 8 decisions:
- Repository<Object, Long> (not JpaRepository) for pure-aggregation repositories — no entity binding needed
- getSegmentTotals reuses findDeliveryStats — same aggregate row covers both endpoints, no duplicate method
- java.sql.Date return type on projection getDay() for DATE_TRUNC results — convert to LocalDate in service layer
- delivery_rate guard at total_sent==0 — explicit guard, returns 0.0 (no division-by-zero)

Phase 9 decisions:
- ABS() in SQL for SMS_DEBIT and SMS_RESERVATION — response reports positive absolute values (sign convention documented)
- TOPUP_PENDING excluded from credit ledger aggregate query (always amount=0, adds no information)
- String topupStatus param (not enum) in native query — avoids Hibernate enum-binding issues with nativeQuery=true
- java.sql.Timestamp (nullable) for approved_at/rejected_at in TopupHistoryRow — null-guarded in service before .toInstant()
- AppEndpoints.SECURED_MAPPINGS now at 9/10 Map.of() pairs — NEXT admin endpoint must switch to Map.ofEntries()

Phase 10 decisions:
- failureRatePct passes -1.0f through (not converted to 0.0) — Resilience4j returns -1.0 when sliding window not yet full; -1.0 is meaningful to the caller as "insufficient data"
- Provider stats SQL: FAIL_FINALIZED excluded from recipient-level query — FAIL_FINALIZED is only set on parent send_request; recipients reach FAILED (not FAIL_FINALIZED) after bad DR
- All-time totals for HLTH-02/HLTH-03 (no date filters) — requirement does not specify filters; can add in a future phase
- Map.ofEntries() MANDATORY from phase 10 onward — SECURED_MAPPINGS must use Map.ofEntries(); future phases must NOT revert to Map.of()

Phase 11 decisions (plan 03):
- ADMIN_AUDIT is the 11th Map.ofEntries() entry — Map.of() is capped at 10 pairs; Map.ofEntries() mandatory from phase 10 onward
- @PreAuthorize at class level on AdminAuditResource — mirrors AdminHealthResource; cleaner than per-method for single-role controllers
- AdminAuditResource delegates entirely to AuditEventService.findEvents() — zero business logic in resource layer; resource layer only maps HTTP params to service call
- SMS_SEND_SUBMITTED fires on first-submission path only — idempotent early-return path is NOT audited (established in plan 02, confirmed in plan 03)
- WEBHOOK_DELETED defined in enum but unused — no delete endpoint in v8 contract; emit only when delete is implemented

Phase 11 decisions (plan 02):
- AuditEventType passed as param to ApiKeyService.createKey/revokeKey — single service serves both admin and client paths; resource layer passes the correct type
- SMS audit detail contains recipientCount only (not phone numbers) — minimizes PII in audit log per AUDT-03
- WEBHOOK_DELETED left unwired — no delete endpoint in v8 contract; enum exists for future use
- @EventListener (synchronous) + REQUIRES_NEW — mirrors AccountChangeEventListener + TrailService; no need for AFTER_COMMIT delay when REQUIRES_NEW suspends outer TX immediately

Phase 12 decisions (plan 01):
- CLIENT_ANALYTICS not in SECURED_MAPPINGS — /v1/** catch-all covers it in JWT chain; constant exists only for securityMatcher reference in ClientSecurityConfiguration
- No @PreAuthorize on ClientAnalyticsResource — @Order(1) API-key chain enforces auth; mirrors SmsResource pattern
- clientId logged at DEBUG only — PII guard; INFO/WARN logs must not expose client identity
- getClientSegmentTotals reuses repository.findDeliveryStats — same aggregate row provides total_segments; no duplicate query needed
- getClientNetCreditsConsumed reads getNetCreditsConsumed() only from SpendSummaryRow — per-type breakdown intentionally excluded per CANL-03 scope

Phase 11 decisions (plan 01):
- AuditEventEntity extends BaseEntity only (not AbstractAuditingEntity) — audit_event is append-only; AbstractAuditingEntity adds status + auditing columns that are unwanted for an immutable audit table
- client_id is nullable FK on audit_event — admin events have a client target but future event types must not be constrained to require one
- DomainAuditEvent is a plain record (not ApplicationEvent subclass) — published via ApplicationEventPublisher; simpler, no framework coupling
- findEvents nativeQuery=true requires explicit countQuery — Spring cannot derive count from native SQL with conditional WHERE; omitting throws at runtime

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

Last session: 2026-03-11T23:15:00Z
Stopped at: Completed 12-01-PLAN.md (client analytics endpoints — CANL-01/02/03, phase 12, plan 1 of 1)
Resume file: None
