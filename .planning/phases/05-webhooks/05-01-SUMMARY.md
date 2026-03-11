---
phase: 05-webhooks
plan: 01
subsystem: api
tags: [webhook, spring-retry, rest-template, flyway, jpa, tsid]

# Dependency graph
requires:
  - phase: 04-provider-integration
    provides: DrCallbackService.finalizeParentIfAllTerminal — the finalization hook that Plan 05-02 will wire to publish SmsFinalisedEvent
  - phase: 03-send-sms
    provides: SendRequestStatus enum, SendRequest entity, client security chain (/v1/**)
provides:
  - V7 Flyway migration: webhook_endpoint and webhook_delivery tables
  - WebhookEndpoint and WebhookDelivery JPA entities
  - WebhookEndpointRepository and WebhookDeliveryRepository
  - WebhookStatus and WebhookDeliveryStatus enums
  - RegisterWebhookRequest and RegisterWebhookResponse contract records
  - SmsFinalisedEvent POJO record in contract/event package
  - WebhookConfig: @EnableRetry + webhookRestTemplate bean (3s/5s timeouts)
  - WebhookService.register() — upsert semantics for webhook registration
  - POST /v1/webhooks endpoint (WebhookResource)
affects: [05-02-webhook-delivery]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - attempt_status column separate from inherited status (EntityStatus) — same convention as send_status in SendRequest
    - TSID PKs — no sequence DDL in V7 migration (consistent with V2-V6)
    - @EnableRetry in isolated WebhookConfig rather than ClientConfig to co-locate retry infrastructure
    - SecurityContextHolder principal extraction (consistent with SmsResource, CreditResource, TopupResource)
    - wh_ prefixed public_id generated from UUID substring to avoid exposing BIGINT PK

key-files:
  created:
    - src/main/resources/db/migration/V7__webhook.sql
    - src/main/java/com/softropic/sendam/client/repo/WebhookEndpoint.java
    - src/main/java/com/softropic/sendam/client/repo/WebhookEndpointRepository.java
    - src/main/java/com/softropic/sendam/client/repo/WebhookDelivery.java
    - src/main/java/com/softropic/sendam/client/repo/WebhookDeliveryRepository.java
    - src/main/java/com/softropic/sendam/client/contract/WebhookStatus.java
    - src/main/java/com/softropic/sendam/client/contract/WebhookDeliveryStatus.java
    - src/main/java/com/softropic/sendam/client/contract/RegisterWebhookRequest.java
    - src/main/java/com/softropic/sendam/client/contract/RegisterWebhookResponse.java
    - src/main/java/com/softropic/sendam/client/contract/event/SmsFinalisedEvent.java
    - src/main/java/com/softropic/sendam/client/config/WebhookConfig.java
    - src/main/java/com/softropic/sendam/client/service/WebhookService.java
    - src/main/java/com/softropic/sendam/client/api/WebhookResource.java
  modified: []

key-decisions:
  - "attempt_status column (PENDING/DELIVERED/FAILED/EXHAUSTED) used in webhook_delivery instead of status — avoids collision with AbstractAuditingEntity inherited status column (EntityStatus)"
  - "WebhookEndpointRepository.findByClientIdAndStatus uses EntityStatus not WebhookStatus — the inherited status column carries entity lifecycle (ACTIVE/INACTIVE/DELETED)"
  - "WebhookDeliveryRepository.findByAttemptStatusAndNextAttemptAtBefore queries attempt_status column for the PENDING poller query"
  - "@EnableRetry placed in WebhookConfig, not ClientConfig — retry infrastructure co-located with webhookRestTemplate"
  - "SecurityContextHolder principal extraction (not @AuthenticationPrincipal ClientPrincipal) — ClientPrincipal does not exist in codebase; consistent with existing resources"

patterns-established:
  - "Pattern: separate lifecycle column when AbstractAuditingEntity.status would conflict — name it <domain>_status or attempt_status"
  - "Pattern: wh_ prefix + UUID substring for public IDs — no Sqids needed for simple prefixed IDs"

# Metrics
duration: 7min
completed: 2026-03-11
---

# Phase 5 Plan 01: Webhook Infrastructure Summary

**POST /v1/webhooks with upsert registration, Flyway V7 tables (webhook_endpoint + webhook_delivery), JPA entities, Spring Retry config, and SmsFinalisedEvent POJO ready for Plan 05-02 wiring**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-11T02:10:20Z
- **Completed:** 2026-03-11T02:17:34Z
- **Tasks:** 2
- **Files modified:** 13 created

## Accomplishments
- V7 Flyway migration creates webhook_endpoint (UNIQUE client_id) and webhook_delivery (partial index on attempt_status=PENDING) tables — no sequence DDL needed due to TSID PKs
- Full JPA entity pair (WebhookEndpoint, WebhookDelivery) with correct column mapping, avoiding the AbstractAuditingEntity.status collision
- POST /v1/webhooks returns {webhook_id, status, created_at} with upsert: existing clients get URL updated and same wh_xxx id returned; new clients get new wh_xxx id
- WebhookConfig declares @EnableRetry and webhookRestTemplate with 3s/5s timeouts
- SmsFinalisedEvent POJO record (contract/event package) ready for DrCallbackService to publish in Plan 05-02

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway V7, entities, repos, and contract types** - `38ff565` (feat)
2. **Task 2: WebhookConfig, WebhookService.register(), and WebhookResource** - `5e61d2a` (feat)

**Plan metadata:** (see below)

## Files Created/Modified
- `src/main/resources/db/migration/V7__webhook.sql` - Two tables: webhook_endpoint and webhook_delivery with TSID PKs
- `src/main/java/com/softropic/sendam/client/repo/WebhookEndpoint.java` - JPA entity for webhook_endpoint
- `src/main/java/com/softropic/sendam/client/repo/WebhookEndpointRepository.java` - findByClientId, findByClientIdAndStatus
- `src/main/java/com/softropic/sendam/client/repo/WebhookDelivery.java` - JPA entity for webhook_delivery (attemptStatus field)
- `src/main/java/com/softropic/sendam/client/repo/WebhookDeliveryRepository.java` - findByAttemptStatusAndNextAttemptAtBefore for poller
- `src/main/java/com/softropic/sendam/client/contract/WebhookStatus.java` - ACTIVE enum
- `src/main/java/com/softropic/sendam/client/contract/WebhookDeliveryStatus.java` - PENDING/DELIVERED/FAILED/EXHAUSTED enum
- `src/main/java/com/softropic/sendam/client/contract/RegisterWebhookRequest.java` - record with @NotBlank url, @NotNull events
- `src/main/java/com/softropic/sendam/client/contract/RegisterWebhookResponse.java` - record with @JsonProperty webhook_id, status, created_at
- `src/main/java/com/softropic/sendam/client/contract/event/SmsFinalisedEvent.java` - POJO record with nested RecipientSummary
- `src/main/java/com/softropic/sendam/client/config/WebhookConfig.java` - @EnableRetry + webhookRestTemplate bean
- `src/main/java/com/softropic/sendam/client/service/WebhookService.java` - register() upsert + ApplicationEventPublisher injected
- `src/main/java/com/softropic/sendam/client/api/WebhookResource.java` - POST /v1/webhooks

## Decisions Made

- **attempt_status vs status column**: `AbstractAuditingEntity` already maps a `status` column to `EntityStatus`. Adding a second `@Column(name="status")` for `WebhookDeliveryStatus` would cause a Hibernate mapping collision. Named the delivery lifecycle column `attempt_status` to match the `send_status` convention established in Phase 3.

- **WebhookStatus enum not used on entity**: `WebhookEndpoint` uses the inherited `EntityStatus` for its lifecycle (ACTIVE/INACTIVE/DELETED). `WebhookStatus.ACTIVE` is a contract-layer type only; the service returns `EntityStatus.ACTIVE.name()` ("ACTIVE") in the response — same string value, no confusion at the API boundary.

- **@EnableRetry in WebhookConfig**: The plan required `@EnableRetry` isolated from `ClientConfig` so webhook retry infrastructure is self-contained. This is also consistent with the research recommendation.

- **SecurityContextHolder over @AuthenticationPrincipal**: The plan referenced `@AuthenticationPrincipal ClientPrincipal` but `ClientPrincipal` does not exist in the codebase. All existing resources (SmsResource, CreditResource, TopupResource) use `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` cast to `Long`. Used the existing pattern.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Webhook delivery lifecycle column renamed to attempt_status**
- **Found during:** Task 1 (WebhookDelivery entity and V7 SQL)
- **Issue:** Plan specified `status` field as `WebhookDeliveryStatus` enum on `WebhookDelivery`, but `AbstractAuditingEntity` already maps a `status` column to `EntityStatus`. A second `@Column(name="status")` mapping would cause a Hibernate column collision at startup.
- **Fix:** Named the delivery lifecycle column `attempt_status` in both V7 SQL and the entity field (`attemptStatus`). Repository query method renamed to `findByAttemptStatusAndNextAttemptAtBefore` accordingly. The `status` column remains EntityStatus per base class.
- **Files modified:** V7__webhook.sql, WebhookDelivery.java, WebhookDeliveryRepository.java
- **Verification:** `mvn compile -q` exits 0; all 124 tests pass

**2. [Rule 1 - Bug] WebhookResource uses SecurityContextHolder not @AuthenticationPrincipal ClientPrincipal**
- **Found during:** Task 2 (WebhookResource)
- **Issue:** Plan specified `@AuthenticationPrincipal ClientPrincipal principal` but `ClientPrincipal` class does not exist in the codebase. The actual principal is a `Long` clientId stored directly in the Spring Security `Authentication`.
- **Fix:** Used `(Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal()` consistent with SmsResource, CreditResource, and TopupResource.
- **Files modified:** WebhookResource.java
- **Verification:** Consistent with all 3 existing resource classes; pattern is already tested indirectly by integration tests

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs in the plan)
**Impact on plan:** Both fixes necessary for correct compilation and operation. No scope creep.

## Issues Encountered
None — compilation succeeded on first attempt after applying the deviation fixes.

## User Setup Required
None — no external service configuration required. webhookRestTemplate is a pure Spring bean.

## Next Phase Readiness
- All static artifacts that Plan 05-02 requires are in place:
  - `SmsFinalisedEvent` importable from any package (placed in `client/contract/event/`)
  - `WebhookDelivery` and `WebhookEndpoint` repos ready for the listener and poller
  - `WebhookService` has `ApplicationEventPublisher` wired (constructor complete)
  - `@EnableRetry` active via `WebhookConfig`
  - `webhookRestTemplate` bean qualified and available for `@Qualifier("webhookRestTemplate")`
- Plan 05-02 will add: `SmsFinalisedListener` (@TransactionalEventListener), `dispatchPendingDeliveries()` scheduled poller, and `postWebhook()` @Retryable method

---
*Phase: 05-webhooks*
*Completed: 2026-03-11*
