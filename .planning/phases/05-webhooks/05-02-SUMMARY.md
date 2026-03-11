---
phase: 05-webhooks
plan: 02
subsystem: infra
tags: [webhook, transactional-event-listener, spring-retry, rest-template, scheduled, exponential-backoff, jpa]

# Dependency graph
requires:
  - phase: 05-01
    provides: WebhookDelivery entity, WebhookDeliveryRepository, WebhookEndpointRepository, SmsFinalisedEvent POJO, WebhookConfig @EnableRetry, webhookRestTemplate bean
  - phase: 04-provider-integration
    provides: DrCallbackService.finalizeParentIfAllTerminal — the finalization hook wired to publish SmsFinalisedEvent
provides:
  - SmsFinalisedEvent published by DrCallbackService after parent FINALIZED/FAIL_FINALIZED
  - SmsFinalisedListener: @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) writes one WebhookDelivery row per recipient per active endpoint
  - WebhookService.dispatchPendingDeliveries(): @Scheduled(fixedDelay=30s) poller for PENDING rows
  - WebhookService.postWebhook(): @Retryable(maxAttempts=2) inner HTTP call with 500ms/1s backoff
  - Exponential backoff schedule: attempt 2->+1min, 3->+5min, 4->+30min, 5->+2h, 6+->EXHAUSTED
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) — post-commit write pattern that prevents phantom delivery rows from rolled-back finalizations
    - Explicit constructor for @Qualifier injection — Lombok @RequiredArgsConstructor does not support @Qualifier on fields; explicit constructor used instead
    - DB-backed retry state survives JVM restarts — @Retryable used only for inner transient retries, not the outer retry loop

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/infrastructure/listener/SmsFinalisedListener.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/DrCallbackService.java
    - src/main/java/com/softropic/sendam/client/service/WebhookService.java

key-decisions:
  - "Explicit constructor injection for @Qualifier('webhookRestTemplate') RestTemplate — Lombok @RequiredArgsConstructor ignores @Qualifier on fields; explicit constructor is the correct pattern"
  - "SmsFinalisedListener uses EntityStatus.ACTIVE (not WebhookStatus) — WebhookEndpointRepository.findByClientIdAndStatus takes EntityStatus; WebhookStatus is contract-layer only"
  - "SmsFinalisedListener sets delivery.setAttemptStatus() not delivery.setStatus() — WebhookDelivery.attemptStatus is the delivery lifecycle field; status column is EntityStatus from AbstractAuditingEntity"
  - "postWebhook() is public on WebhookService — required for Spring AOP proxy to intercept @Retryable; private methods bypass AOP"
  - "Event published AFTER sendRequestRepository.save(parent) — @TransactionalEventListener(AFTER_COMMIT) guarantees delivery rows only written if parent save commits"

patterns-established:
  - "Pattern: @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) always paired — AFTER_COMMIT alone leaves no ambient TX; REQUIRES_NEW opens a fresh one for the listener write"
  - "Pattern: explicit constructor over @RequiredArgsConstructor when @Qualifier needed"

# Metrics
duration: 8min
completed: 2026-03-11
---

# Phase 5 Plan 02: Webhook Delivery Summary

**End-to-end webhook delivery wired: DrCallbackService publishes SmsFinalisedEvent post-commit, SmsFinalisedListener fans out into per-recipient PENDING WebhookDelivery rows, and WebhookService poller dispatches with 5-attempt exponential backoff (1min/5min/30min/2h/EXHAUSTED)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-11T02:19:00Z
- **Completed:** 2026-03-11T02:27:59Z
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- DrCallbackService injects ApplicationEventPublisher and publishes SmsFinalisedEvent after parent save in finalizeParentIfAllTerminal — the event carries all recipient summaries for fan-out
- SmsFinalisedListener receives the post-commit event in a REQUIRES_NEW transaction and writes one WebhookDelivery row per recipient per active endpoint — idempotent because the outer TERMINAL_STATUSES guard in DrCallbackService prevents duplicate finalization events
- WebhookService.dispatchPendingDeliveries() polls every 30s (fixedDelay), calls postWebhook() (@Retryable for fast inner retries), advances status to DELIVERED on 2xx, or applies DB-persisted backoff on failure — retry state survives JVM restarts

## Task Commits

Each task was committed atomically:

1. **Task 1: Publish SmsFinalisedEvent from DrCallbackService and create SmsFinalisedListener** - `4e19027` (feat)
2. **Task 2: WebhookService delivery poller with exponential backoff** - `97bf8c3` (feat)

**Plan metadata:** (see below)

## Files Created/Modified
- `src/main/java/com/softropic/sendam/client/service/DrCallbackService.java` - Added ApplicationEventPublisher field + publishEvent call after sendRequestRepository.save(parent)
- `src/main/java/com/softropic/sendam/client/infrastructure/listener/SmsFinalisedListener.java` - New file: @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW), fans out per recipient, builds JSON payload via ObjectMapper with manual fallback
- `src/main/java/com/softropic/sendam/client/service/WebhookService.java` - Added WebhookDeliveryRepository + RestTemplate fields, dispatchPendingDeliveries(), dispatchOne(), applyFailure(), nextAttemptAt(), postWebhook() @Retryable, postWebhookFallback() @Recover; switched to explicit constructor for @Qualifier

## Decisions Made

- **Explicit constructor over @RequiredArgsConstructor**: Plan specified `@Qualifier("webhookRestTemplate") RestTemplate` field injection. Lombok `@RequiredArgsConstructor` generates a constructor from `final` fields but cannot propagate `@Qualifier` to the constructor parameter. An explicit constructor is required for Spring to use the qualifier when resolving the `RestTemplate` bean. This is the standard Spring pattern; it is not a deviation but a plan implementation detail.

- **EntityStatus.ACTIVE in SmsFinalisedListener**: The plan's listener code referenced `WebhookStatus.ACTIVE` in the endpoint lookup, but `WebhookEndpointRepository.findByClientIdAndStatus` takes `EntityStatus` (the inherited entity lifecycle status), not `WebhookStatus` (a contract-layer enum). Used `EntityStatus.ACTIVE` to match the actual repository signature.

- **`setAttemptStatus()` not `setStatus()`**: The plan referred to `delivery.setStatus(WebhookDeliveryStatus.PENDING)`, but the actual `WebhookDelivery` entity field is `attemptStatus` (established in Plan 05-01 to avoid the AbstractAuditingEntity.status column collision). All listener and poller code uses `setAttemptStatus()` and `findByAttemptStatusAndNextAttemptAtBefore()`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SmsFinalisedListener uses EntityStatus.ACTIVE not WebhookStatus.ACTIVE**
- **Found during:** Task 1 (SmsFinalisedListener)
- **Issue:** Plan's listener pseudocode called `findByClientIdAndStatus(event.clientId(), WebhookStatus.ACTIVE)` but `WebhookEndpointRepository.findByClientIdAndStatus` takes `EntityStatus` (the inherited entity lifecycle column), not the contract-layer `WebhookStatus` enum. This was a plan error that would have caused a compile failure.
- **Fix:** Used `EntityStatus.ACTIVE` matching the actual repository signature.
- **Files modified:** SmsFinalisedListener.java
- **Verification:** `mvn compile -q` exits 0; 124 tests pass

**2. [Rule 1 - Bug] WebhookDelivery field is `attemptStatus` not `status`**
- **Found during:** Task 1 (SmsFinalisedListener) and Task 2 (WebhookService)
- **Issue:** Plan pseudocode used `delivery.setStatus(WebhookDeliveryStatus.PENDING)` and `findByStatusAndNextAttemptAtBefore(WebhookDeliveryStatus.PENDING, now)`, but Plan 05-01 named the field `attemptStatus` to avoid collision with the inherited `EntityStatus status` column. Using the wrong setter/query would compile but either silently overwrite EntityStatus or produce a Hibernate query error.
- **Fix:** Used `delivery.setAttemptStatus()` in listener and `webhookDeliveryRepository.findByAttemptStatusAndNextAttemptAtBefore()` in poller.
- **Files modified:** SmsFinalisedListener.java, WebhookService.java
- **Verification:** `mvn compile -q` exits 0; 124 tests pass

**3. [Rule 1 - Bug] Explicit constructor required for @Qualifier injection**
- **Found during:** Task 2 (WebhookService)
- **Issue:** Plan stated `@Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate` as a field with `@RequiredArgsConstructor`. Lombok `@RequiredArgsConstructor` cannot propagate field-level `@Qualifier` to the generated constructor parameter — Spring would fail to disambiguate `RestTemplate` beans at startup.
- **Fix:** Replaced `@RequiredArgsConstructor` with an explicit constructor carrying `@Qualifier("webhookRestTemplate")` on the `RestTemplate` parameter.
- **Files modified:** WebhookService.java
- **Verification:** `mvn compile -q` exits 0; 124 tests pass

---

**Total deviations:** 3 auto-fixed (all Rule 1 — bugs in the plan pseudocode; all necessary for compilation and correct runtime behavior)
**Impact on plan:** All three fixes were required for correctness. No scope creep. The core delivery pipeline is wired exactly as specified.

## Issues Encountered
None — compilation succeeded after applying the three deviation fixes. All 124 prior tests passed with zero regressions.

## User Setup Required
None — webhook delivery is driven by the scheduled poller; no additional environment variables or external service configuration required. The `webhookRestTemplate` bean is self-contained in `WebhookConfig`.

## Next Phase Readiness
- Phase 5 (Webhooks) is COMPLETE — WEBHOOK-01, WEBHOOK-02, WEBHOOK-03 all satisfied
- Full delivery pipeline in place: finalization triggers event -> listener writes PENDING rows -> poller dispatches -> success=DELIVERED, failure=backoff up to 5 attempts then EXHAUSTED
- All v1.0 milestone plans are now complete (Phases 1-5)

---
*Phase: 05-webhooks*
*Completed: 2026-03-11*
