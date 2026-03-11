---
phase: 11-audit-log
plan: "02"
subsystem: api
tags: [spring-events, application-event-publisher, event-listener, audit, transactional]

# Dependency graph
requires:
  - phase: 11-01
    provides: AuditEventType enum, DomainAuditEvent record, AuditEventEntity, AuditEventRepository

provides:
  - AuditEventService with @Transactional(REQUIRES_NEW) record() and @Transactional(readOnly) findEvents()
  - AuditEventListener @EventListener swallowing exceptions so audit failure never breaks callers
  - ApplicationEventPublisher injected into ClientService, TopupService, ApiKeyService, SmsService
  - publishEvent(DomainAuditEvent) calls wired at all AUDT-01 through AUDT-04 trigger points

affects:
  - 11-03 (admin query API — AuditEventService.findEvents() is the read side)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuditEventListener @EventListener (synchronous) + AuditEventService @Transactional(REQUIRES_NEW) — mirrors AccountChangeEventListener + TrailService pattern"
    - "Audit event type passed as parameter from caller resource layer — ApiKeyService accepts AuditEventType so a single service serves both admin and client paths"
    - "publishEvent placed AFTER the domain operation succeeds, NEVER inside a catch block"

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/audit/service/AuditEventService.java
    - src/main/java/com/softropic/sendam/gateway/audit/service/AuditEventListener.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/account/service/ClientService.java
    - src/main/java/com/softropic/sendam/gateway/billing/service/TopupService.java
    - src/main/java/com/softropic/sendam/gateway/auth/service/ApiKeyService.java
    - src/main/java/com/softropic/sendam/gateway/auth/api/AdminApiKeyResource.java
    - src/main/java/com/softropic/sendam/gateway/auth/api/ClientApiKeyResource.java
    - src/main/java/com/softropic/sendam/gateway/sms/service/SmsService.java
    - src/main/java/com/softropic/sendam/gateway/webhook/service/WebhookService.java

key-decisions:
  - "AuditEventType passed as param to ApiKeyService.createKey/revokeKey — single service handles both admin and client paths; caller (AdminApiKeyResource vs ClientApiKeyResource) determines the event type"
  - "SMS audit detail contains recipientCount (integer) only, not phone numbers — minimizes PII in audit log per AUDT-03"
  - "WEBHOOK_DELETED left unwired — no delete endpoint in v8 contract; enum exists for future use"
  - "@EventListener (synchronous) chosen over @TransactionalEventListener(AFTER_COMMIT) — REQUIRES_NEW on AuditEventService suspends the outer TX immediately; no need for AFTER_COMMIT delay"

patterns-established:
  - "Audit failure isolation: try/catch in AuditEventListener logs and swallows — core operation outcome is preserved"
  - "publishEvent placement rule: AFTER the final domain save(), BEFORE the return statement — event fires only on success path"

# Metrics
duration: 9min
completed: 2026-03-11
---

# Phase 11 Plan 02: Audit Write Pipeline Summary

**AuditEventService (REQUIRES_NEW isolation) + AuditEventListener (exception-swallowing @EventListener) + publishEvent hooks in 5 services — AUDT-01 through AUDT-04 events now automatically recorded**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-11T21:34:20Z
- **Completed:** 2026-03-11T21:43:17Z
- **Tasks:** 2/2
- **Files modified:** 9 (7 main sources + 2 new + 6 test files)

## Accomplishments

- Created AuditEventService with `@Transactional(REQUIRES_NEW)` on record() ensuring audit rows survive outer TX rollbacks; findEvents() delegates to AuditEventRepository for the read path
- Created AuditEventListener with synchronous `@EventListener` that catches all exceptions and swallows them — audit failure never propagates to the calling operation
- Wired publishEvent calls in ClientService, TopupService, ApiKeyService, SmsService, and WebhookService at the correct trigger points (after domain save, before return)

## Task Commits

Each task was committed atomically:

1. **Task 1: AuditEventService and AuditEventListener** - `ec6438d` (feat)
2. **Task 2: Hook injection — audit events wired into 5 services** - `5c0ffc6` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/audit/service/AuditEventService.java` — service with REQUIRES_NEW record() and readOnly findEvents()
- `src/main/java/com/softropic/sendam/gateway/audit/service/AuditEventListener.java` — @EventListener on DomainAuditEvent; swallows exceptions
- `src/main/java/com/softropic/sendam/gateway/account/service/ClientService.java` — added ApplicationEventPublisher; publishEvent(CLIENT_CREATED) after createClient
- `src/main/java/com/softropic/sendam/gateway/billing/service/TopupService.java` — added ApplicationEventPublisher; publishEvent(TOPUP_APPROVED/REJECTED) in approve/reject
- `src/main/java/com/softropic/sendam/gateway/auth/service/ApiKeyService.java` — added ApplicationEventPublisher; createKey/revokeKey accept AuditEventType param
- `src/main/java/com/softropic/sendam/gateway/auth/api/AdminApiKeyResource.java` — passes ADMIN_API_KEY_CREATED/REVOKED to ApiKeyService
- `src/main/java/com/softropic/sendam/gateway/auth/api/ClientApiKeyResource.java` — passes CLIENT_API_KEY_CREATED/REVOKED to ApiKeyService
- `src/main/java/com/softropic/sendam/gateway/sms/service/SmsService.java` — added ApplicationEventPublisher; publishEvent(SMS_SEND_SUBMITTED) on first-submission path only
- `src/main/java/com/softropic/sendam/gateway/webhook/service/WebhookService.java` — publishEvent(WEBHOOK_REGISTERED) in createNew(); publishEvent(WEBHOOK_UPDATED) in updateExisting()

## Decisions Made

- **AuditEventType as param to ApiKeyService:** Single service handles both admin and client API key operations. The resource layer (AdminApiKeyResource vs ClientApiKeyResource) knows the semantic context and passes the correct type. This avoids duplication of key generation logic.
- **SMS detail = recipientCount only:** Phone numbers in the audit log would be unnecessary PII. AUDT-03 says count only — detail string records `sendRequestId` and `recipients=N`.
- **WEBHOOK_DELETED left unwired:** The v8 contract has no webhook delete endpoint. The enum value exists for future use. No trigger point invented.
- **Synchronous @EventListener + REQUIRES_NEW:** The AuditEventService carries REQUIRES_NEW which immediately suspends the outer TX. This mirrors the established AccountChangeEventListener + TrailService pattern in the security module.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated test files to match new ApiKeyService.createKey/revokeKey signatures**

- **Found during:** Task 2 (mvn test run)
- **Issue:** 5 test files called the old 2-argument `createKey(Long, String)` and 2-argument `revokeKey(Long, Long)` that no longer exist after adding the AuditEventType parameter
- **Fix:** Added `AuditEventType` import and argument to `createKey`/`revokeKey` calls in `AdminApiKeyResourceTest`, `ClientApiKeySecurityIT`, and `ApiKeyServiceTest`; added `@Mock ApplicationEventPublisher` to `TopupServiceTest`, `SmsServiceTest`, and `WebhookServiceTest` so Mockito can inject the new field
- **Files modified:** 6 test files
- **Verification:** `mvn test` passes with 156/156 tests, 0 failures
- **Committed in:** `5c0ffc6` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required for BUILD SUCCESS. No scope creep — pure test compatibility updates following the method signature change mandated by the plan's AuditEventType-as-param design.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 03 (admin query API) can proceed immediately:
- AuditEventService.findEvents() is ready to serve the admin REST endpoint
- All event types are being written by the service hooks; the audit_event table will have real data after any client/admin operation
- AdminAuditResource needs to expose findEvents() behind a paginated GET endpoint

No blockers.

---
*Phase: 11-audit-log*
*Completed: 2026-03-11*
