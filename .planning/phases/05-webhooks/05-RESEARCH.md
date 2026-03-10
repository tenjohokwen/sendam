# Phase 5: Webhooks - Research

**Researched:** 2026-03-10
**Domain:** Webhook registration, event delivery, retry-with-backoff, Spring Retry
**Confidence:** HIGH

---

## Summary

Phase 5 adds outbound webhook delivery: clients register a URL, the gateway POSTs an
`sms.finalized` event when each message is finalized, and failed deliveries are retried
with exponential backoff until acknowledged.

The codebase already contains every dependency needed. `spring-retry` is in the pom.
`spring-boot-starter-web` provides `RestTemplate`. PostgreSQL and Flyway are already wired.
No new library additions are required.

The recommended pattern is **database-backed delivery with scheduled retry polling**:
- A `webhook_endpoint` table stores client registrations (one per client for v1).
- A `webhook_delivery` table stores one row per delivery attempt, with `status`
  (PENDING, DELIVERED, FAILED, EXHAUSTED) and `next_attempt_at`.
- `DrCallbackService.finalizeParentIfAllTerminal` (the existing finalization hook) publishes
  a Spring `ApplicationEvent` after each SendRequest reaches FINALIZED or FAIL_FINALIZED.
- A `@EventListener` (or `@TransactionalEventListener`) writes pending delivery rows.
- A `@Scheduled` poller dispatches due deliveries via `RestTemplate` and advances state.
- `spring-retry` `@Retryable` is NOT used for the outer retry loop (wrong granularity —
  retry must survive across JVM restarts). Use it only for the single HTTP attempt with
  short transient retries (connect timeout / 5xx) within one delivery cycle.

**Primary recommendation:** Database-backed delivery table + scheduled poller. Retries
survive server restarts. State is auditable. No new infrastructure required.

---

## Standard Stack

All libraries already present in `pom.xml`. Zero new dependencies.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-retry` | managed by Spring Boot | Per-attempt HTTP retry with backoff | Already in pom; idiomatic Spring retry without Resilience4j overhead |
| `spring-boot-starter-web` (RestTemplate) | 3.5.11 | Outbound HTTP POST to client URLs | Already used in NexahClient; consistent pattern |
| Spring Data JPA | 3.5.11 | Webhook entity persistence | Already in pom; consistent with all other repos |
| Flyway | managed | Schema migration for new tables | Already in pom and wired |
| `@TransactionalEventListener` (Spring) | built-in | Trigger delivery row creation AFTER commit | Prevents phantom rows if the surrounding TX rolls back |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@Scheduled` (Spring) | built-in | Poller for due webhook deliveries | Already used in SmsSchedulerService; same pattern |
| `@EnableAsync` (Spring) | built-in | Async dispatch to avoid blocking scheduler thread | Add to ClientConfig if dispatch takes > 1s |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DB-backed retry | `@Retryable` only | `@Retryable` retries die with JVM; DB survives restarts |
| `RestTemplate` | `WebClient` | WebClient is reactive; rest of codebase uses RestTemplate — stay consistent |
| `ApplicationEventPublisher` | direct method call | Events decouple DrCallbackService from WebhookService; publisher is the right pattern here |

**Installation:** No new dependencies required.

---

## Architecture Patterns

### Recommended Project Structure

New files belong in the `client` module following existing conventions:

```
client/
├── api/
│   └── WebhookResource.java          # POST /v1/webhooks, GET /v1/webhooks
├── contract/
│   ├── WebhookStatus.java            # ACTIVE (enum)
│   ├── RegisterWebhookRequest.java   # url, events[]
│   ├── RegisterWebhookResponse.java  # webhook_id, status, created_at
│   ├── WebhookDeliveryStatus.java    # PENDING, DELIVERED, FAILED, EXHAUSTED (enum)
│   └── event/
│       └── SmsFinalisedEvent.java    # Spring ApplicationEvent carrying clientId + SendRequest data
├── service/
│   └── WebhookService.java           # register(), dispatch logic, retry poller
├── repo/
│   ├── WebhookEndpoint.java          # @Entity — stores client URL + event subscriptions
│   ├── WebhookEndpointRepository.java
│   ├── WebhookDelivery.java          # @Entity — one row per delivery attempt
│   └── WebhookDeliveryRepository.java
└── infrastructure/
    └── listener/
        └── SmsFinalisedListener.java  # @TransactionalEventListener — writes PENDING delivery rows
```

The `SmsFinalisedEvent` crosses from `DrCallbackService` (service layer) to
`SmsFinalisedListener` (infrastructure layer), so it belongs in
`client/contract/event/` following the ARCHITECTURE.md rule for cross-layer events.

---

### Pattern 1: Transactional Event Publishing

**What:** `DrCallbackService.finalizeParentIfAllTerminal` publishes a `SmsFinalisedEvent`
immediately before returning. The listener is annotated `@TransactionalEventListener(phase =
TransactionPhase.AFTER_COMMIT)` so delivery rows are only created if the finalization
transaction committed successfully.

**When to use:** Any time delivery-side writes must be causally consistent with a domain
state change. This is the standard pattern for "fire-and-forget after commit" in Spring.

**Example:**
```java
// In DrCallbackService.finalizeParentIfAllTerminal, after sendRequestRepository.save(parent):
// Source: Spring @TransactionalEventListener docs
eventPublisher.publishEvent(new SmsFinalisedEvent(this,
        parent.getClientId(),
        parent.getSendRequestId(),
        parent.getId(),
        recipientSummaries));

// In SmsFinalisedListener (infrastructure/listener):
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onSmsFinalized(SmsFinalisedEvent event) {
    // Load active webhooks for this client, write PENDING delivery rows
    webhookEndpointRepository
        .findByClientIdAndStatus(event.clientId(), WebhookStatus.ACTIVE)
        .forEach(endpoint -> {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWebhookEndpointId(endpoint.getId());
            delivery.setClientId(event.clientId());
            delivery.setPayload(buildPayload(event));   // JSON string
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setNextAttemptAt(Instant.now());
            delivery.setAttemptCount(0);
            webhookDeliveryRepository.save(delivery);
        });
}
```

Note: `@TransactionalEventListener` by default runs in the same transaction as the
publisher. Using `Propagation.REQUIRES_NEW` on the listener ensures the delivery row is
committed separately — if the listener fails, it does not roll back the finalization TX.

---

### Pattern 2: Scheduled Delivery Poller

**What:** A `@Scheduled(fixedDelay = ...)` method polls `webhook_delivery` for rows where
`status = PENDING AND next_attempt_at <= NOW()`, sends each via HTTP POST, then updates
status to DELIVERED or increments attempt count and sets the next backoff timestamp.

**When to use:** Always — this is the mechanism that drives actual delivery and retry.

**Example:**
```java
// In WebhookService (service layer):
@Scheduled(fixedDelay = 30_000)   // every 30s, consistent with SmsSchedulerService cadence
@Transactional
public void dispatchPendingDeliveries() {
    Instant now = Instant.now();
    List<WebhookDelivery> due = webhookDeliveryRepository
        .findByStatusAndNextAttemptAtBefore(WebhookDeliveryStatus.PENDING, now);

    for (WebhookDelivery delivery : due) {
        try {
            dispatchOne(delivery);
        } catch (Exception e) {
            log.error("Unexpected error dispatching delivery id={}", delivery.getId(), e);
            // advance to next attempt without crashing the poller
        }
    }
}
```

---

### Pattern 3: Per-Attempt HTTP Call with Spring Retry

**What:** The single HTTP attempt within `dispatchOne()` is wrapped with `@Retryable` to
handle transient network errors (connection reset, 5xx) within one delivery cycle. This
gives two fast retries before the outer backoff machinery handles longer waits.

**When to use:** Only for the inner HTTP call. NOT for the outer retry loop (that is the
poller's job).

**Example:**
```java
// In WebhookService:
@Retryable(
    retryFor = { RestClientException.class },
    maxAttempts = 2,
    backoff = @Backoff(delay = 500, multiplier = 2.0)
)
public ResponseEntity<String> postWebhook(String url, String payload) {
    return webhookRestTemplate.postForEntity(url, payload, String.class);
}

@Recover
public ResponseEntity<String> postWebhookFallback(RestClientException e, String url, String payload) {
    // Rethrow — dispatchOne() catches this and applies outer backoff
    throw new WebhookDeliveryException("HTTP call failed after inner retries", e);
}
```

`@EnableRetry` must be added to `ClientConfig` (or a new `WebhookConfig`).

---

### Pattern 4: Exponential Backoff Schedule (Database-Computed)

**What:** After each failed attempt, `next_attempt_at` is set by the service using a
formula derived from attempt count. Backoff is computed in application code, not by
Spring Retry, because the retry must survive JVM restarts.

**Recommended schedule:**
| Attempt | Delay Before Next | Cumulative |
|---------|-------------------|------------|
| 1 (first try) | 1 min | 1 min |
| 2 | 5 min | 6 min |
| 3 | 30 min | 36 min |
| 4 | 2 hours | ~2.5 hours |
| 5 | 6 hours | ~8.5 hours |
| 6+ | EXHAUSTED | — |

Max 5 attempts (attempt 1–5, then EXHAUSTED). These numbers are conservative enough for
SMS delivery notifications without hammering a client endpoint that is down.

```java
private Instant nextAttemptAt(int nextAttemptNumber) {
    return switch (nextAttemptNumber) {
        case 2 -> Instant.now().plusSeconds(60);
        case 3 -> Instant.now().plusSeconds(300);
        case 4 -> Instant.now().plusSeconds(1800);
        case 5 -> Instant.now().plusSeconds(7200);
        default -> null;   // EXHAUSTED — no further attempts
    };
}
```

---

### Anti-Patterns to Avoid

- **Calling webhook HTTP from within finalizeParentIfAllTerminal transaction:** This blocks
  the finalization TX for the duration of the outbound HTTP call — potential deadlock and
  guaranteed slow path. Always decouple via event + poller.

- **Using @Retryable alone as the retry mechanism:** Spring Retry retries happen in-process
  and are lost on JVM restart. All retry state must live in the database.

- **Storing webhook payload as individual columns:** The payload is a fixed schema
  (`sms.finalized` event). Store as a VARCHAR JSON string — simpler than JSONB for this
  use case, and avoids adding a dependency on PostgreSQL JSONB mapping.

- **One webhook delivery row per recipient instead of per send_request:** The v8 contract
  shows the delivery event contains `sendRequestId`, `recipient`, `gateway_message_id`, and
  `status`. The `sms.finalized` event fires once per individual message (recipient), not
  once per send request. Check the contract carefully — see section 12.2. Plan for one
  delivery row per recipient finalization.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Retry scheduling | Custom `ScheduledExecutorService` | `@Scheduled` poller + DB `next_attempt_at` | Already used in SmsSchedulerService; consistent |
| Per-attempt transient retry | Manual loop | `@Retryable` from existing `spring-retry` dep | Handles IOException/5xx in one annotation |
| Post-commit event | Direct service call from DrCallbackService | `@TransactionalEventListener(AFTER_COMMIT)` | Prevents delivery rows for rolled-back finalizations |
| HTTP client for webhook POST | Separate HTTP library | `RestTemplate` (already used in NexahClient) | No new dep; consistent pattern |

**Key insight:** Every mechanism needed exists. The only new code is the domain logic
(registration, delivery state machine, backoff formula) and two new DB tables.

---

## Common Pitfalls

### Pitfall 1: TransactionalEventListener Propagation Trap

**What goes wrong:** Listener annotated with `@TransactionalEventListener` but without
`@Transactional(propagation = REQUIRES_NEW)` — the listener runs in NO transaction by
default after the outer TX commits, so `webhookDeliveryRepository.save()` throws because
there is no active transaction.

**Why it happens:** `@TransactionalEventListener(phase = AFTER_COMMIT)` runs after the
outer transaction has committed. There is no ambient transaction at that point.

**How to avoid:** Always annotate the listener method with both
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` AND
`@Transactional(propagation = Propagation.REQUIRES_NEW)`.

**Warning signs:** `javax.persistence.TransactionRequiredException` or silent no-ops on
`save()` during testing.

---

### Pitfall 2: Poller Blocking on Slow Client Endpoints

**What goes wrong:** The `@Scheduled(fixedDelay)` poller processes deliveries synchronously.
One client endpoint that times out at 30s blocks the entire poller from processing other
deliveries.

**Why it happens:** fixedDelay waits for the previous execution to complete. If 10 due
deliveries each time out, the poller runs for 300s before the next cycle.

**How to avoid:** Set a short connect+read timeout on the webhook `RestTemplate` (e.g.,
connectTimeout=3s, readTimeout=5s). This bounds the worst case to `n * 5s` per cycle.
Optionally process per-delivery in `@Async` threads for full isolation, but that adds
complexity — the timeout approach is sufficient for v1.

**Warning signs:** Poller falling behind, growing PENDING backlog despite no errors.

---

### Pitfall 3: Duplicate Delivery on Race Condition

**What goes wrong:** Two poller runs overlap (fixedDelay does not prevent concurrent
execution if the poller itself calls async methods and returns immediately). The same
delivery row is dispatched twice.

**Why it happens:** If `dispatchPendingDeliveries` returns quickly and fixedDelay resets,
the next scheduled run may find the same PENDING rows before the async dispatch updates
their status.

**How to avoid:** Keep dispatch synchronous within the poller (consistent with
SmsSchedulerService pattern). The existing codebase uses fixedDelay for the same reason —
"prevents overlapping runs" is already a documented decision.

**Warning signs:** Duplicate `sms.finalized` POSTs to client endpoints.

---

### Pitfall 4: webhook_id Exposed as Sequential Integer

**What goes wrong:** The DB `id` (BIGINT sequence) is returned as `webhook_id` in the API
response, leaking internal row counts to clients.

**Why it happens:** Direct mapping of JPA entity id to API response.

**How to avoid:** Generate the `webhook_id` as a prefixed alphanumeric identifier (e.g.,
`wh_` + base36 of the internal id, or a UUID-based string) consistent with how the v8
contract shows `wh_12345`. The existing codebase does not appear to use Sqids for public
IDs despite having the `sqids` library in the pom — use a simple approach: store a
`public_id` VARCHAR column populated at creation (e.g., `"wh_" + UUID.randomUUID().toString().replace("-","").substring(0,12)`).

---

### Pitfall 5: v8 Contract Payload Ambiguity — Per-Recipient vs Per-Request

**What goes wrong:** The contract section 12.2 shows a delivery event with a single
`recipient` field, implying the event fires per recipient. Building a per-send-request
event model would send one webhook with the wrong shape.

**Why it happens:** Misreading the contract (section 12.1 says "sms.finalized" event, and
the example in 12.2 has one recipient per event object).

**How to avoid:** Fire one webhook delivery per recipient finalization (one row per
`SendRequestRecipient` that reaches FINALIZED or FAIL_FINALIZED). This means
`SmsFinalisedEvent` carries one recipient's data, and `finalizeParentIfAllTerminal` must
publish N events for N recipients, or the listener fans out from a single event.

The simpler approach: publish one event per parent finalization carrying all recipient
summaries, then the listener fans out into one `WebhookDelivery` row per recipient.

---

## Code Examples

### WebhookEndpoint Entity (Flyway migration shape)

```sql
-- Source: V5__send_request.sql pattern (existing codebase)
CREATE TABLE main.webhook_endpoint (
    id                  BIGINT PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    public_id           VARCHAR(30) NOT NULL UNIQUE,   -- "wh_xxx"
    url                 VARCHAR(2048) NOT NULL,
    events              VARCHAR(100) NOT NULL DEFAULT 'sms.finalized',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT,
    CONSTRAINT uq_webhook_endpoint_client UNIQUE (client_id)  -- one per client, v1
);

CREATE TABLE main.webhook_delivery (
    id                  BIGINT PRIMARY KEY,
    webhook_endpoint_id BIGINT NOT NULL REFERENCES main.webhook_endpoint(id),
    client_id           BIGINT NOT NULL REFERENCES main.client_account(id),
    send_request_id     VARCHAR(200) NOT NULL,
    recipient           VARCHAR(20) NOT NULL,
    gateway_message_id  VARCHAR(100),
    delivery_status     VARCHAR(20) NOT NULL,          -- DELIVERED / FAIL_FINALIZED
    payload             TEXT NOT NULL,                  -- JSON string of the event
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- delivery status
    attempt_count       INT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP NOT NULL,
    last_attempt_at     TIMESTAMP,
    http_status         INT,
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);

CREATE INDEX idx_webhook_delivery_due
    ON main.webhook_delivery(status, next_attempt_at)
    WHERE status = 'PENDING';
```

---

### SmsFinalisedEvent (contract/event)

```java
// Source: ARCHITECTURE.md event placement rule — cross-layer events go in contract/event/
public record SmsFinalisedEvent(
    Object source,
    Long clientId,
    String sendRequestId,
    List<RecipientSummary> recipients
) implements ApplicationEvent {
    public record RecipientSummary(
        String recipient,
        String gatewayMessageId,
        SendRequestStatus finalStatus   // FINALIZED or FAIL_FINALIZED
    ) {}
}
```

Note: Spring `ApplicationEvent` is a class, not an interface. Either extend it or use
`@EventListener` on a POJO method (Spring 4.2+ supports POJO events without extending
ApplicationEvent). Use the POJO approach for cleaner record syntax:

```java
// contract/event/SmsFinalisedEvent.java — plain record, no extends needed
public record SmsFinalisedEvent(
    Long clientId,
    String sendRequestId,
    List<RecipientSummary> recipients
) {}
```

---

### Webhook Delivery Payload (JSON)

Matches v8 contract section 12.2 exactly:

```json
{
  "event": "sms.finalized",
  "sendRequestId": "req-20260303-001",
  "recipient": "+237655123456",
  "gateway_message_id": "msg_gw_01HQ2R2Z7F5",
  "status": "DELIVERED"
}
```

`status` is `"DELIVERED"` when `FINALIZED`, `"FAILED"` when `FAIL_FINALIZED`. The contract
uses the semantic status, not the internal enum name.

---

### WebhookRestTemplate Configuration

A dedicated `RestTemplate` for webhook delivery with short timeouts:

```java
// In WebhookConfig or ClientConfig — separate from NexahClient's RestTemplate
@Bean("webhookRestTemplate")
public RestTemplate webhookRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);   // 3s connect
    factory.setReadTimeout(5000);       // 5s read
    return new RestTemplate(new BufferingClientHttpRequestFactory(factory));
}
```

---

### @EnableRetry Placement

`@EnableRetry` must be added to an existing `@Configuration` class. Add it to `ClientConfig`:

```java
@Configuration
@EnableScheduling
@EnableRetry                                    // add this
@EnableConfigurationProperties(NexahProperties.class)
public class ClientConfig {}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Retryable` as full retry loop | DB-backed poller + `@Retryable` for inner attempt only | Standard since Spring Boot 2.x | Retry state survives restarts |
| `ApplicationEvent` extending `ApplicationEvent` class | POJO events with `@EventListener` | Spring 4.2 (2015) | Cleaner record syntax possible |
| `@TransactionalEventListener` without `REQUIRES_NEW` | Must add `REQUIRES_NEW` propagation | Spring 4.2 trap | Avoids silent no-ops post-commit |

**Deprecated/outdated:**
- Extending `ApplicationEvent` class directly: still works but unnecessary; POJO events
  are preferred.

---

## Open Questions

1. **Retry count limit**
   - What we know: the v8 contract says "retried with backoff until the endpoint
     acknowledges" — no explicit max stated.
   - What's unclear: does "until acknowledged" mean infinite retries? Infinite retry is
     impractical; a bounded max (5 attempts) is standard practice.
   - Recommendation: cap at 5 attempts, move to EXHAUSTED. Document this as an
     implementation detail not contradicting the contract.

2. **HMAC signing of webhook payloads**
   - What we know: section 12 of the v8 contract does not specify any `X-Signature` header
     or HMAC verification.
   - What's unclear: clients cannot verify authenticity of incoming webhooks without a
     signature. Many production webhook systems include `X-Webhook-Signature`.
   - Recommendation: omit in v1 (contract doesn't require it); plan for it in v2. Do not
     add a `secret` column to `webhook_endpoint` now.

3. **One webhook endpoint per client vs multiple**
   - What we know: v8 contract `POST /v1/webhooks` returns `webhook_id` implying a
     collection. But in the simplest model one endpoint per client suffices.
   - What's unclear: can a client register multiple webhook URLs?
   - Recommendation: enforce `UNIQUE(client_id)` in V7 migration for v1. If the spec
     wanted multiple, it would include a list endpoint (`GET /v1/webhooks`). The contract
     doesn't show a list endpoint — one per client is sufficient.

---

## Sources

### Primary (HIGH confidence)
- Existing codebase — `DrCallbackService.java`, `SmsSchedulerService.java`,
  `NexahClient.java`, `ClientConfig.java`, `V5__send_request.sql` — direct inspection
- `ARCHITECTURE.md` — package placement rules
- `requirements/client-facing_API_contract_v8.md` sections 12.1 and 12.2 — webhook API spec
- `pom.xml` — confirmed `spring-retry`, `RestTemplate`, Flyway, JPA all present

### Secondary (MEDIUM confidence)
- [Spring Retry GitHub](https://github.com/spring-projects/spring-retry) — `@Retryable`,
  `@Backoff`, `@Recover`, `@EnableRetry` annotation API confirmed
- [How to Implement Retry with Exponential Backoff in Spring](https://oneuptime.com/blog/post/2026-01-25-retry-exponential-backoff-spring/view) — backoff parameter patterns
- [WebClient Retry Patterns for Spring Boot Services](https://medium.com/@AlexanderObregon/webclient-retry-patterns-for-spring-boot-services-b2fdc1e96eec) — confirmed RestTemplate as appropriate choice over WebClient for non-reactive codebase
- [Spring Boot Retry Example — HowToDoInJava](https://howtodoinjava.com/spring-boot2/spring-retry-module/) — `@Retryable` parameter details (retryFor, maxAttempts, @Backoff)

### Tertiary (LOW confidence)
- [Webhook with Spring Boot — Vincenzo Racca](https://www.vincenzoracca.com/en/blog/framework/spring/spring-webhook/) — general webhook pattern reference, not verified against official docs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed present in pom.xml, no new deps needed
- Architecture: HIGH — patterns derived from direct inspection of existing codebase
  conventions (DrCallbackService, SmsSchedulerService, NexahClient, ARCHITECTURE.md)
- Pitfalls: HIGH for TransactionalEventListener trap (documented Spring behavior);
  MEDIUM for payload shape (contract section 12.2 inspected but not fully specified)

**Research date:** 2026-03-10
**Valid until:** 2026-04-10 (stable Spring Boot + spring-retry ecosystem)
