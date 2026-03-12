---
phase: 16-sms-monitoring-webhooks
plan: 01
subsystem: api
tags: [spring-boot, jpa, jpql, rest, admin, sms, webhooks, pageable]

# Dependency graph
requires:
  - phase: 05-sms-gateway
    provides: SendRequest, SendRequestRecipient entities and repositories
  - phase: 10-webhook-delivery
    provides: WebhookEndpoint, WebhookDelivery entities and repositories
  - phase: 13-admin-foundation
    provides: AppEndpoints security constants pattern and SECURED_MAPPINGS

provides:
  - Four admin REST endpoints for SMS and webhook monitoring
  - ADMIN_SMS_MONITOR (/api/admin/sms/monitor/**) security constant
  - ADMIN_WEBHOOK_MONITOR (/api/admin/webhooks/**) security constant
  - ScheduledSmsRow and DlrRow DTO records in gateway/sms/contract
  - WebhookEndpointRow and WebhookDeliveryRow DTO records in gateway/webhook/contract
  - AdminSmsMonitorService and AdminWebhookMonitorService Spring beans
  - AdminSmsMonitorResource (SMSM-01, SMSM-02) and AdminWebhookMonitorResource (WEBH-01, WEBH-02)

affects:
  - 16-02-frontend-pages (SmsMonitorPage.vue and WebhooksPage.vue consume these endpoints)
  - 18-testing (integration tests for admin monitor endpoints)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Admin monitor service pattern: service class maps entity Page to DTO record Page using .map()
    - Pageable-overload pattern: derived query List method and Pageable Page method coexist in same repository interface
    - Optional-filter JPQL pattern: :param IS NULL OR d.field = :param for nullable filter params

key-files:
  created:
    - src/main/java/com/softropic/sendam/gateway/sms/contract/ScheduledSmsRow.java
    - src/main/java/com/softropic/sendam/gateway/sms/contract/DlrRow.java
    - src/main/java/com/softropic/sendam/gateway/webhook/contract/WebhookEndpointRow.java
    - src/main/java/com/softropic/sendam/gateway/webhook/contract/WebhookDeliveryRow.java
    - src/main/java/com/softropic/sendam/gateway/sms/service/AdminSmsMonitorService.java
    - src/main/java/com/softropic/sendam/gateway/webhook/service/AdminWebhookMonitorService.java
    - src/main/java/com/softropic/sendam/gateway/sms/api/AdminSmsMonitorResource.java
    - src/main/java/com/softropic/sendam/gateway/webhook/api/AdminWebhookMonitorResource.java
  modified:
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java
    - src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRepository.java
    - src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRecipientRepository.java
    - src/main/java/com/softropic/sendam/gateway/webhook/repo/WebhookDeliveryRepository.java

key-decisions:
  - "ADMIN_SMS_MONITOR = /api/admin/sms/monitor/** (not /api/admin/sms/**) — preserves sibling ADMIN_ANALYTICS = /api/admin/sms/analytics/**"
  - "findBySendRequestId(String) added as derived query — plan noted to add if missing, it was missing"
  - "WebhookEndpointRow.status uses EntityStatus.name() (not WebhookStatus) — entity uses EntityStatus for lifecycle, no separate WebhookStatus field"
  - "Live 401/403 verification not possible without DB — confirmed via compiled class existence and pre-existing test suite pass"

patterns-established:
  - "Optional-filter JPQL: (:param IS NULL OR d.field = :param) in @Query for nullable filter params in admin list endpoints"
  - "Admin monitor service: Spring @Service accepts Pageable, queries repo, maps Page<Entity> to Page<DTO record> inline"

# Metrics
duration: 21min
completed: 2026-03-12
---

# Phase 16 Plan 01: SMS & Webhook Monitor Backend Summary

**Four admin REST endpoints backed by JPQL pageable queries: GET /api/admin/sms/monitor/scheduled, GET /api/admin/sms/monitor/scheduled/{id}/dlr, GET /api/admin/webhooks/endpoints, GET /api/admin/webhooks/deliveries — all secured to ADMIN role only**

## Performance

- **Duration:** 21 min
- **Started:** 2026-03-12T19:56:18Z
- **Completed:** 2026-03-12T20:17:04Z
- **Tasks:** 2/2
- **Files modified:** 12

## Accomplishments

- Two new security path constants (ADMIN_SMS_MONITOR, ADMIN_WEBHOOK_MONITOR) wired into AppEndpoints.SECURED_MAPPINGS with ADMIN-only access
- Four pageable JPQL query methods added across SendRequestRepository, SendRequestRecipientRepository, and WebhookDeliveryRepository
- Four DTO records (ScheduledSmsRow, DlrRow, WebhookEndpointRow, WebhookDeliveryRow) created in their respective contract packages
- Two admin service classes and two REST controllers implement all four SMSM-01/SMSM-02/WEBH-01/WEBH-02 endpoints with `@PreAuthorize("hasRole('ADMIN')")`

## Task Commits

Each task was committed atomically:

1. **Task 1: Security constants, repo queries, DTO records** - `f527d24` (feat)
2. **Task 2: Admin service classes and REST controllers** - `4ddc3fd` (feat)

**Plan metadata:** (next commit — docs)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - Added ADMIN_SMS_MONITOR and ADMIN_WEBHOOK_MONITOR constants and SECURED_MAPPINGS entries
- `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRepository.java` - Added findScheduledAccepted(Pageable) JPQL + findBySendRequestId(String) derived query
- `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRecipientRepository.java` - Added findBySendRequestIdFk(Long, Pageable) pageable overload
- `src/main/java/com/softropic/sendam/gateway/webhook/repo/WebhookDeliveryRepository.java` - Added findByFilters(Long, WebhookDeliveryStatus, Pageable) JPQL query
- `src/main/java/com/softropic/sendam/gateway/sms/contract/ScheduledSmsRow.java` - New DTO record for scheduled SMS rows
- `src/main/java/com/softropic/sendam/gateway/sms/contract/DlrRow.java` - New DTO record for per-recipient DLR rows
- `src/main/java/com/softropic/sendam/gateway/webhook/contract/WebhookEndpointRow.java` - New DTO record for webhook endpoint rows
- `src/main/java/com/softropic/sendam/gateway/webhook/contract/WebhookDeliveryRow.java` - New DTO record for webhook delivery rows
- `src/main/java/com/softropic/sendam/gateway/sms/service/AdminSmsMonitorService.java` - New service: getScheduledSms and getDlrForRequest
- `src/main/java/com/softropic/sendam/gateway/webhook/service/AdminWebhookMonitorService.java` - New service: getEndpoints and getDeliveries with optional filter
- `src/main/java/com/softropic/sendam/gateway/sms/api/AdminSmsMonitorResource.java` - REST controller /api/admin/sms/monitor (SMSM-01, SMSM-02)
- `src/main/java/com/softropic/sendam/gateway/webhook/api/AdminWebhookMonitorResource.java` - REST controller /api/admin/webhooks (WEBH-01, WEBH-02)

## Decisions Made

- **ADMIN_SMS_MONITOR path**: Used `/api/admin/sms/monitor/**` not `/api/admin/sms/**` to avoid subsuming the existing `ADMIN_ANALYTICS = "/api/admin/sms/analytics/**"` sibling path.
- **WebhookEndpointRow.status field**: Uses `e.getStatus().name()` (EntityStatus enum) — the WebhookEndpoint entity has no separate WebhookStatus field; EntityStatus is the lifecycle status.
- **findBySendRequestId derived query**: Added to SendRequestRepository because AdminSmsMonitorService.getDlrForRequest() needs to resolve the String sendRequestId to the Long PK for the recipient FK lookup. The plan flagged this as a conditional addition and it was indeed missing.
- **Live server verification**: No PostgreSQL running in this environment so spring-boot:run fails at DB connection. Compilation success + compiled class file existence + passing pre-existing test suite used as verification proxy.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added findBySendRequestId(String) to SendRequestRepository**
- **Found during:** Task 2 (AdminSmsMonitorService implementation)
- **Issue:** AdminSmsMonitorService.getDlrForRequest() calls sendRequestRepository.findBySendRequestId(sendRequestId) to resolve the String ID to the Long PK, but the method did not exist in the repository
- **Fix:** Added `Optional<SendRequest> findBySendRequestId(String sendRequestId)` as a Spring Data derived query method
- **Files modified:** `src/main/java/com/softropic/sendam/gateway/sms/repo/SendRequestRepository.java`
- **Verification:** Compilation succeeds; the plan itself noted "If findBySendRequestId does not already exist, add it"
- **Committed in:** f527d24 (Task 1 commit — added proactively when reviewing repo before writing service)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential for getDlrForRequest() to function. No scope creep.

## Issues Encountered

- PostgreSQL not running in dev environment — spring-boot:run fails at DB connection. This is an infrastructure constraint, not a code issue. All four endpoints are confirmed registered via compiled class files and the `@RequestMapping` + `@PreAuthorize` annotations in source.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All four backend endpoints are ready for the Phase 16 frontend (SmsMonitorPage.vue, WebhooksPage.vue)
- Security path constants in AppEndpoints ensure routes are ADMIN-protected at the filter level AND at the method level via @PreAuthorize
- No blockers for Phase 16 Plan 02 (frontend pages)

---
*Phase: 16-sms-monitoring-webhooks*
*Completed: 2026-03-12*
