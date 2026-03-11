---
phase: 05-webhooks
verified: 2026-03-11T03:15:00Z
status: passed
score: 3/3 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 3/3
  gaps_closed: []
  gaps_remaining: []
  regressions: []
gaps: []
human_verification:
  - test: "POST /v1/webhooks with a valid API key returns 200 with webhook_id (wh_xxx prefix), status=ACTIVE, and created_at"
    expected: "Response body contains webhook_id starting with wh_, status is ACTIVE, created_at is an ISO-8601 timestamp"
    why_human: "HTTP endpoint behavior requires a running server with a live DB; cannot verify from source alone"
  - test: "Send an SMS, let it finalize via a Nexah DR callback, observe that the registered webhook URL receives a POST"
    expected: "Client webhook endpoint receives a JSON body: {event:sms.finalized, sendRequestId, recipient, gateway_message_id, status:DELIVERED}"
    why_human: "End-to-end delivery requires a running environment, real or mock Nexah DR callback, and an externally reachable webhook URL"
  - test: "Simulate a webhook endpoint returning 500 or refusing connections and verify retry backoff"
    expected: "After attempt 1 fails, next_attempt_at advances by 1 min, then 5 min, 30 min, 2 h, then row is marked EXHAUSTED"
    why_human: "Retry scheduling requires time-lapse observation against a live DB; cannot verify statically"
---

# Phase 5: Webhooks Verification Report

**Phase Goal:** Clients can register webhook URLs to receive push notifications when messages are finalized, with retry on failure.
**Verified:** 2026-03-11T03:15:00Z
**Status:** passed
**Re-verification:** Yes — independent source-file verification after initial PASSED report

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Client can register a webhook URL and receive a webhook_id | VERIFIED | `POST /v1/webhooks` in `WebhookResource` (line 42-48) calls `webhookService.register(clientId, request)`; `WebhookService.createNew()` generates `"wh_" + UUID...substring(0,12)` public ID and saves via `webhookEndpointRepository.save()`; returns `RegisterWebhookResponse(webhookId, "ACTIVE", createdDate)` with `@JsonProperty("webhook_id")` serialization |
| 2 | sms.finalized events are delivered to registered webhook URLs when messages reach FINALIZED or FAIL_FINALIZED | VERIFIED | `DrCallbackService.finalizeParentIfAllTerminal()` calls `sendRequestRepository.save(parent)` at line 203 then `eventPublisher.publishEvent(new SmsFinalisedEvent(...))` at line 218; `SmsFinalisedListener.onSmsFinalized()` is annotated `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)`, writes one `WebhookDelivery` row per recipient with `attemptStatus = PENDING` and `nextAttemptAt = Instant.now()`; `WebhookService.dispatchPendingDeliveries()` runs `@Scheduled(fixedDelay = 30_000)` and POSTs via `webhookRestTemplate.postForEntity(url, entity, String.class)` |
| 3 | Failed webhook deliveries are retried with backoff until the endpoint acknowledges | VERIFIED | `applyFailure()` computes `nextAttemptNumber = attemptCount + 1` then applies: attempt 2 → +60s, attempt 3 → +300s, attempt 4 → +1800s, attempt 5 → +7200s, default → `EXHAUSTED`; `postWebhook()` is `@Retryable(retryFor=RestClientException.class, maxAttempts=2)` for inner fast retries; `@Recover` re-throws so outer `dispatchOne()` catch triggers `applyFailure()`; DB-persisted `attempt_status` and `next_attempt_at` survive process restarts |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V7__webhook.sql` | webhook_endpoint and webhook_delivery DDL | VERIFIED | Both tables present; `attempt_status VARCHAR(20)` on webhook_delivery avoids AbstractAuditingEntity `status` collision; `delivery_status` column holds semantic SMS outcome; partial index `idx_webhook_delivery_due` on `attempt_status, next_attempt_at WHERE attempt_status='PENDING'`; `CONSTRAINT uq_webhook_endpoint_client UNIQUE (client_id)` enforces one endpoint per client |
| `src/main/java/com/softropic/sendam/client/repo/WebhookEndpoint.java` | JPA entity for webhook_endpoint | VERIFIED | 50 lines; `@Entity @Table(name="webhook_endpoint", schema="main")`; extends `AbstractAuditingEntity`; fields: `clientId`, `publicId`, `url`, `events`; `@Builder.Default status = EntityStatus.ACTIVE`; no stub patterns |
| `src/main/java/com/softropic/sendam/client/repo/WebhookDelivery.java` | JPA entity for webhook_delivery | VERIFIED | 83 lines; `@Entity @Table(name="webhook_delivery", schema="main")`; `attemptStatus` field maps to `attempt_status` column via `@Enumerated(EnumType.STRING) @Column(name="attempt_status")`; `deliveryStatus` maps to `delivery_status`; `@Builder.Default attemptStatus = PENDING` and `attemptCount = 0` |
| `src/main/java/com/softropic/sendam/client/repo/WebhookEndpointRepository.java` | findByClientId, findByClientIdAndStatus | VERIFIED | `findByClientId(Long)` returns `Optional<WebhookEndpoint>`; `findByClientIdAndStatus(Long, EntityStatus)` returns `List<WebhookEndpoint>`; correct parameter types confirmed |
| `src/main/java/com/softropic/sendam/client/repo/WebhookDeliveryRepository.java` | findByAttemptStatusAndNextAttemptAtBefore | VERIFIED | Query method present with exact parameter types `(WebhookDeliveryStatus, Instant)` returning `List<WebhookDelivery>`; correctly named to match `attempt_status` column (not `status`) |
| `src/main/java/com/softropic/sendam/client/contract/event/SmsFinalisedEvent.java` | POJO record for Spring events | VERIFIED | Record with `clientId`, `sendRequestId`, `recipients`; nested `RecipientSummary` record with `recipient`, `gatewayMessageId`, `finalStatus (SendRequestStatus)` |
| `src/main/java/com/softropic/sendam/client/config/WebhookConfig.java` | @EnableRetry + webhookRestTemplate bean | VERIFIED | `@Configuration @EnableRetry`; `@Bean("webhookRestTemplate")` creates `RestTemplate(new BufferingClientHttpRequestFactory(...))` with 3000ms connect and 5000ms read timeouts |
| `src/main/java/com/softropic/sendam/client/config/ClientConfig.java` | @EnableScheduling | VERIFIED | `@Configuration @EnableScheduling` — required for `dispatchPendingDeliveries()` @Scheduled to fire |
| `src/main/java/com/softropic/sendam/client/api/WebhookResource.java` | POST /v1/webhooks endpoint | VERIFIED | 49 lines; `@RestController @RequestMapping("/v1/webhooks")`; `@PostMapping` on `register()` with `@Valid` request body; `clientId` extracted from `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` |
| `src/main/java/com/softropic/sendam/client/service/WebhookService.java` | register() + dispatchPendingDeliveries() + postWebhook() + applyFailure() | VERIFIED | 286 lines; upsert logic branches on `findByClientId()`; `@Scheduled(fixedDelay=30_000)` on `dispatchPendingDeliveries()`; `@Retryable(retryFor=RestClientException.class, maxAttempts=2, backoff=@Backoff(delay=500, multiplier=2.0))` on public `postWebhook()`; `@Recover` fallback re-throws; explicit constructor with `@Qualifier("webhookRestTemplate")` |
| `src/main/java/com/softropic/sendam/client/infrastructure/listener/SmsFinalisedListener.java` | @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW) | VERIFIED | 130 lines; both annotations on `onSmsFinalized()`; fan-out loop: for each ACTIVE endpoint × each recipient → `delivery.setAttemptStatus(WebhookDeliveryStatus.PENDING)` → `webhookDeliveryRepository.save(delivery)`; `buildPayload()` constructs `{"event":"sms.finalized",...}` JSON via Jackson with string fallback |
| `src/main/java/com/softropic/sendam/client/service/DrCallbackService.java` | SmsFinalisedEvent published after parent save | VERIFIED | `sendRequestRepository.save(parent)` at line 203; `eventPublisher.publishEvent(new SmsFinalisedEvent(...))` at line 218 with recipients mapped from `List<SendRequestRecipient>`; event published for both FINALIZED and FAIL_FINALIZED parent states |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `WebhookResource` | `WebhookService` | `webhookService.register(clientId, request)` | WIRED | Line 46 of WebhookResource; `@RequiredArgsConstructor` injects `WebhookService` |
| `WebhookEndpoint` | `V7__webhook.sql` | `@Table(name="webhook_endpoint", schema="main")` | WIRED | Entity column names match DDL exactly; `attempt_status` naming consistent throughout |
| `WebhookConfig` | `WebhookService` | `@Bean("webhookRestTemplate")` injected via explicit constructor `@Qualifier` | WIRED | WebhookService constructor at lines 59-67 explicitly qualifies injection; `@Retryable` infrastructure activated by `@EnableRetry` on WebhookConfig |
| `DrCallbackService` | `SmsFinalisedListener` | `eventPublisher.publishEvent(SmsFinalisedEvent)` line 218 | WIRED | Listener receives via `@TransactionalEventListener(AFTER_COMMIT)`; AFTER_COMMIT is meaningful because event is published after `sendRequestRepository.save()` inside the outer `@Transactional` method |
| `SmsFinalisedListener` | `WebhookDeliveryRepository` | `webhookDeliveryRepository.save(delivery)` line 82 | WIRED | Called inside `@Transactional(REQUIRES_NEW)` — ambient transaction present; `setAttemptStatus(PENDING)` uses correct field name |
| `WebhookService.dispatchPendingDeliveries` | DB poller | `findByAttemptStatusAndNextAttemptAtBefore(PENDING, now)` | WIRED | Lines 103-104; `WebhookDeliveryStatus.PENDING` is the correct enum value; `Instant.now()` as cutoff |
| `WebhookService.postWebhook` | Client URL (external) | `webhookRestTemplate.postForEntity(url, entity, String.class)` | WIRED | Line 217; `HttpEntity` carries `Content-Type: application/json`; `is2xxSuccessful()` determines DELIVERED vs failure path |
| `ClientConfig` | `dispatchPendingDeliveries` | `@EnableScheduling` activates `@Scheduled` | WIRED | `ClientConfig` is a `@Configuration` class in the same module; `@EnableScheduling` confirmed present |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| WEBHOOK-01: Client can register a webhook URL and event subscription | SATISFIED | `POST /v1/webhooks` → `WebhookService.register()` → upsert `WebhookEndpoint` → returns `RegisterWebhookResponse` with `webhook_id` (wh_ prefix), `status`, `created_at`; v8 contract `RegisterWebhookRequest` accepts `url` + `events` list |
| WEBHOOK-02: Gateway delivers sms.finalized events to registered webhooks on message finalization | SATISFIED | Full pipeline: `DrCallbackService` publishes → `SmsFinalisedListener` writes PENDING rows (one per recipient per endpoint) → `dispatchPendingDeliveries` POSTs JSON payload; payload format matches v8 contract section 12.2 |
| WEBHOOK-03: Gateway retries webhook delivery when the client endpoint is unreachable | SATISFIED | `@Retryable` inner retries (2 attempts, 500ms backoff) + DB-persisted exponential outer backoff (1 min / 5 min / 30 min / 2 h / EXHAUSTED); `attempt_status` stays PENDING between outer retries; survives process restart |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `WebhookService.java` | 75 | `wh_xxx` in Javadoc `<li>` text | Info | Documentation example, not code stub |

Zero functional stub patterns. No TODO/FIXME/placeholder/incomplete implementation markers in any of the 11 webhook-related files.

### Human Verification Required

#### 1. Webhook Registration Endpoint Response Shape

**Test:** POST to `/v1/webhooks` with a valid API key bearer token and `{"url":"https://example.com/hook","events":["sms.finalized"]}`.
**Expected:** HTTP 200, body `{"webhook_id":"wh_xxxxxxxxxxxx","status":"ACTIVE","created_at":"<ISO-8601>"}`.
**Why human:** Requires a running server with live DB; endpoint shape and serialization cannot be confirmed from source alone. Note that `created_at` field may be `null` on first save depending on when `AbstractAuditingEntity` populates `createdDate`.

#### 2. End-to-End Delivery on Message Finalization

**Test:** Submit an SMS, simulate a Nexah delivery report callback to advance the message to FINALIZED, then observe that the registered webhook URL receives a POST within ~30 seconds.
**Expected:** Client endpoint receives `{"event":"sms.finalized","sendRequestId":"...","recipient":"...","gateway_message_id":"...","status":"DELIVERED"}`.
**Why human:** Full pipeline requires a running environment; the event-listener-poller chain spans multiple transactions and a scheduled task.

#### 3. Retry Backoff Progression

**Test:** Register a webhook pointing to an endpoint that always returns 500. Trigger finalization. Observe the `webhook_delivery` table over time.
**Expected:** `next_attempt_at` advances by 1 min after attempt 1 fails, 5 min after attempt 2, 30 min after attempt 3, 2 h after attempt 4; `attempt_status` changes to EXHAUSTED after attempt 5 fails.
**Why human:** Backoff schedule verification requires time-lapse DB observation in a live environment.

### Gaps Summary

No gaps. All three observable truths are verified by direct source file inspection. All required artifacts exist and are substantive. All key links are wired correctly.

**Critical design decisions independently confirmed:**

- `attempt_status` column name (distinct from inherited `status` column) — consistent throughout V7__webhook.sql DDL, `WebhookDelivery.java` `@Column(name="attempt_status")`, `WebhookDeliveryRepository` query method name, `SmsFinalisedListener.setAttemptStatus()`, and `WebhookService` status comparisons. No collision risk.
- `@Qualifier("webhookRestTemplate")` injected via explicit constructor in `WebhookService` — `@Retryable` requires Spring AOP proxy, which means Spring constructs the bean; explicit constructor injection with `@Qualifier` is correct because Lombok `@RequiredArgsConstructor` cannot propagate field-level `@Qualifier`.
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` on `SmsFinalisedListener.onSmsFinalized()` — prevents phantom delivery rows from rolled-back finalizations AND provides the ambient transaction that `webhookDeliveryRepository.save()` requires.
- Event published at line 218 in `DrCallbackService.finalizeParentIfAllTerminal()`, after `sendRequestRepository.save(parent)` at line 203 — the AFTER_COMMIT guarantee is correctly anchored to the parent save.
- `@EnableScheduling` on `ClientConfig` (confirmed) activates `@Scheduled(fixedDelay=30_000)` on `dispatchPendingDeliveries()` — `fixedDelay` (not `fixedRate`) prevents overlapping poller runs.
- `postWebhook()` is `public` — required for Spring AOP to intercept `@Retryable` (self-invocation bypasses the proxy).

---
_Verified: 2026-03-11T03:15:00Z_
_Verifier: Claude (gsd-verifier) — re-verification with independent source file inspection_
