# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-11)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 11 — Audit Log

## Current Position

Phase: 11 of 12 (Audit Log)
Plan: 2 of 3 — COMPLETE
Status: In progress
Last activity: 2026-03-11 — Completed 11-02-PLAN.md (audit write pipeline, 2/2 tasks, AuditEventService + AuditEventListener + 5 service hooks)

Progress: v1.0 COMPLETE | v1.1 ████░ 80% (4/5 phases complete, 2/3 plans in phase 11)

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

Phase 11 decisions (plan 02):
- AuditEventType passed as param to ApiKeyService.createKey/revokeKey — single service serves both admin and client paths; resource layer passes the correct type
- SMS audit detail contains recipientCount only (not phone numbers) — minimizes PII in audit log per AUDT-03
- WEBHOOK_DELETED left unwired — no delete endpoint in v8 contract; enum exists for future use
- @EventListener (synchronous) + REQUIRES_NEW — mirrors AccountChangeEventListener + TrailService; no need for AFTER_COMMIT delay when REQUIRES_NEW suspends outer TX immediately

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

Last session: 2026-03-11T22:43:17Z
Stopped at: Completed 11-02-PLAN.md (audit write pipeline — phase 11, plan 2 of 3)
Resume file: None
