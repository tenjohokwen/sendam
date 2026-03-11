# Phase 11: Audit Log - Research

**Researched:** 2026-03-11
**Domain:** Event-driven audit recording; paginated admin query; Spring application events; JPA/PostgreSQL
**Confidence:** HIGH — all findings verified directly from codebase; no external sources needed

---

## Summary

Phase 11 adds a new `audit_event` table that records platform-significant events (admin actions, client
API key ops, SMS sends, webhook config changes) and exposes a paginated admin query endpoint. The
codebase already has a complete audit infrastructure in `security/audit/` that this phase must mirror
and extend — not duplicate.

The dominant pattern in this codebase for cross-cutting event recording is Spring's application event
mechanism: services publish domain events, listeners record them. The existing `TrailService`
(under `security/audit/service/`) already uses `@Transactional(REQUIRES_NEW)` to isolate audit writes
from the caller's transaction. The existing `SecurityAuditListener` and `AccountChangeEventListener`
demonstrate the full event → listener → `TrailService` flow.

**Primary recommendation:** Create a new `audit_event` table in a new `gateway/audit/` module.
Use domain events + a dedicated `AuditEventListener` component that calls a new `AuditEventService`
(mirrors `TrailService` in isolation level). Hook into the five trigger points via
`ApplicationEventPublisher` injected into the relevant services. The admin query endpoint follows the
same `Repository<Object, Long>` + native JPQL pattern used in Phases 8–10.

---

## Standard Stack

### Core — already present in project
| Library | Purpose | Already Used |
|---------|---------|-------------|
| Spring Data JPA | Repository, `@Query`, pagination | Yes — all modules |
| `io.hypersistence.utils` | `@Tsid` ID generation on `BaseEntity` | Yes — all entities |
| Flyway | Schema migrations (`V10__audit_event.sql`) | Yes |
| Spring `ApplicationEventPublisher` | Domain event dispatch | Yes — webhook, email |
| `@TransactionalEventListener` + `@Transactional(REQUIRES_NEW)` | Post-commit write isolation | Yes — `SmsFinalisedListener`, `MailManager` |
| `@EventListener` (synchronous) | In-request event handling | Yes — `SecurityAuditListener` |
| Spring Data `Pageable` / `Page<T>` | Paginated queries | Yes — `SmsService.getStatus` uses manual pagination; prefer Spring Data `Pageable` for the new query endpoint |
| MapStruct | DTO ↔ entity mapping | Yes — `AuditTrailMapper` |
| Lombok | `@RequiredArgsConstructor`, `@Slf4j`, `@Builder` | Yes — all recent modules |

### Supporting
| Concern | Tool | Notes |
|---------|------|-------|
| Security enforcement | `AppEndpoints.SECURED_MAPPINGS` + `@PreAuthorize("hasRole('ADMIN')")` | Add `ADMIN_AUDIT` constant; use `Map.ofEntries()` (mandatory from Phase 10) |
| Native query pagination | `Pageable` param on `@Query(nativeQuery=true)` | Spring Data supports `Pageable` on native queries; use `Page<T>` return |
| Projection interface | Interface with getters matching query column aliases | Pattern: `DeliveryStatRow`, `ProviderStatsRow` |

---

## Architecture Patterns

### Recommended Package Structure

```
gateway/
└── audit/
    ├── api/
    │   └── AdminAuditResource.java          # GET /api/admin/audit/events
    ├── contract/
    │   ├── AuditEventType.java              # Enum: CLIENT_CREATED, TOPUP_APPROVED, ...
    │   ├── AuditEventRow.java               # Projection interface for query results
    │   ├── AuditEventResponse.java          # Paginated response record
    │   └── DomainAuditEvent.java            # Application event POJO published by services
    ├── repo/
    │   ├── AuditEventEntity.java            # @Entity for audit_event table
    │   └── AuditEventRepository.java        # Spring Data repository with native query
    └── service/
        ├── AuditEventService.java           # @Transactional(REQUIRES_NEW) — writes audit rows
        └── AuditEventListener.java          # @Component — subscribes to DomainAuditEvent
```

### Pattern 1: Domain Event → Listener → Service (the hook model)

This is the established pattern in this codebase. Services that need audit hooks call
`applicationEventPublisher.publishEvent(new DomainAuditEvent(...))` after their core logic succeeds.
The listener picks this up and writes the audit row in an isolated transaction.

**Decision: use `@EventListener` (synchronous, same TX) vs `@TransactionalEventListener(AFTER_COMMIT)` (post-commit):**

- Use `@EventListener` (synchronous) when the audit write must NOT silently fail if the main TX
  rolls back and the hook point is inside an existing `@Transactional` method. The existing
  `SecurityAuditListener` and `AccountChangeEventListener` use this because their `TrailService`
  already carries `@Transactional(REQUIRES_NEW)` which opens a separate TX regardless.
- Use `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` when the audit
  row must only appear if the outer TX commits (e.g., SMS send persisted → then audit).

For Phase 11, **`@EventListener` + `TrailService` pattern is simpler and already proven**. The
`AuditEventService` carries `@Transactional(REQUIRES_NEW)` so its write is always in its own
transaction regardless of when the listener fires.

```java
// Source: AccountChangeEventListener.java — established pattern
@EventListener
public void handleAccountChange(AccountChangeEvent event) {
    recordAuditTrail(event);
}
// TrailService already has @Transactional(Propagation.REQUIRES_NEW)
```

The new `AuditEventListener` follows the exact same structure but writes to `audit_event`, not
`audit_log`.

### Pattern 2: New Entity must NOT extend AbstractAuditingEntity

`AbstractAuditingEntity` has a `status` column mapped as `@Column(name = "status")`. The established
invariant is: child entities must NOT re-declare `status`. `AuditEventEntity` is append-only and has
no lifecycle status — extend `BaseEntity` only (gives TSID id, no extra columns).

```java
// Correct pattern for an append-only entity:
@Entity
@Table(name = "audit_event", schema = "main")
public class AuditEventEntity extends BaseEntity {
    // TSID id inherited from BaseEntity
    // No AbstractAuditingEntity — no status column, no auditing columns
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    // ... other fields
}
```

### Pattern 3: Paginated Admin Query

```java
// Source: DeliveryAnalyticsRepository pattern — Repository<Object, Long> for aggregate queries
// For entity-bound paginated queries, extend JpaRepository and use Pageable
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    @Query(value = """
        SELECT
            ae.id            AS id,
            ae.event_type    AS eventType,
            ae.client_id     AS clientId,
            ae.actor         AS actor,
            ae.detail        AS detail,
            ae.occurred_at   AS occurredAt
        FROM main.audit_event ae
        WHERE (:clientId IS NULL OR ae.client_id = :clientId)
          AND (:from IS NULL     OR ae.occurred_at >= :from)
          AND (:to   IS NULL     OR ae.occurred_at <= :to)
        ORDER BY ae.occurred_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM main.audit_event ae
        WHERE (:clientId IS NULL OR ae.client_id = :clientId)
          AND (:from IS NULL     OR ae.occurred_at >= :from)
          AND (:to   IS NULL     OR ae.occurred_at <= :to)
        """,
        nativeQuery = true)
    Page<AuditEventRow> findEvents(
        @Param("clientId") Long clientId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );
}
```

Spring Data JPA supports `Pageable` on native queries when `countQuery` is also provided.
Return type is a projection interface (`AuditEventRow`) — same pattern as `DeliveryStatRow`.

### Pattern 4: AppEndpoints registration (Map.ofEntries mandatory)

Phase 10 decision: `Map.ofEntries()` is mandatory. Add a new constant `ADMIN_AUDIT` and include it.

```java
// In AppEndpoints.java — add:
public static final String ADMIN_AUDIT = "/api/admin/audit/**";

// In the static initializer, add to existing Map.ofEntries():
Map.entry(ADMIN_AUDIT, new String[]{AuthoritiesConstants.ADMIN})
```

### Anti-Patterns to Avoid

- **Extending AbstractAuditingEntity for AuditEventEntity:** The `status` column would collide with
  other uses. AuditEventEntity is append-only; use `BaseEntity` only.
- **Re-using `AuditLog` / `TrailService`:** The existing security audit log is for security events
  (login, auth failures, profile changes). Phase 11 audit events are domain events (SMS sent, API key
  created, etc.). Separate tables, separate services — do not co-mingle.
- **Custom pagination logic:** Do not hand-roll offset/limit as was done in `SmsService.getStatus`.
  Use Spring Data `Pageable` + `Page<T>` — cleaner and tested.
- **Reverting to `Map.of()`:** `SECURED_MAPPINGS` must use `Map.ofEntries()`. `Map.of()` is capped at
  10 entries and was already swapped in Phase 10.
- **Publishing events inside a catch block:** Audit events should only fire when the domain operation
  succeeds. If the operation throws, the event should not be published.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Paginated query | Manual `subList` offset/limit | Spring Data `Pageable` + `Page<T>` on `JpaRepository` | Spring handles count query, page metadata; avoids off-by-one bugs |
| Audit isolation | Manual try/catch and connection handling | `@Transactional(REQUIRES_NEW)` on service | Already established; isolates audit writes from caller TX |
| Cross-cutting hooks | AOP / interceptors | `ApplicationEventPublisher` + `@EventListener` | Established pattern in this codebase; simpler, testable |
| TSID generation | `UUID` + manual conversion | `@Tsid` on `BaseEntity` | Already wired; all entities in this project use it |

---

## Common Pitfalls

### Pitfall 1: AuditEventEntity extending AbstractAuditingEntity
**What goes wrong:** `AbstractAuditingEntity` declares `@Column(name = "status")`. If `AuditEventEntity`
extends it, the inherited `status` column appears in the table — unnecessary for an append-only
audit table and adds Hibernate complexity.
**How to avoid:** Extend `BaseEntity` only. Declare all needed columns explicitly on the entity.

### Pitfall 2: Missing `countQuery` on native paginated query
**What goes wrong:** Spring Data cannot derive a count query from a native SQL query with complex
`WHERE` clauses or `ORDER BY`. The `Page<T>` result will throw at runtime with a missing count query
error.
**How to avoid:** Always supply an explicit `countQuery` attribute alongside `nativeQuery=true` when
returning `Page<T>`.

### Pitfall 3: Publishing audit event inside the service before the operation commits
**What goes wrong:** If `@EventListener` fires synchronously inside the same transaction and the
outer TX later rolls back, the `REQUIRES_NEW` audit write already committed — orphan audit rows.
**How to avoid:** Publish the event only after the core operation succeeds. In `@Transactional` methods
this happens naturally when `publishEvent` is called after the save/update. If tighter guarantees are
needed, switch to `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` (the
`SmsFinalisedListener` pattern).

### Pitfall 4: Adding ADMIN_AUDIT to AppEndpoints with Map.of()
**What goes wrong:** `Map.of()` is capped at 10 key-value pairs. The current `SECURED_MAPPINGS` has
10 entries; adding an 11th with `Map.of()` throws an `IllegalArgumentException` at startup.
**How to avoid:** Use `Map.ofEntries(Map.entry(...), ...)` — already established as mandatory from
Phase 10.

### Pitfall 5: Caller service not injecting ApplicationEventPublisher
**What goes wrong:** Forgetting to wire `ApplicationEventPublisher` into services being hooked
(`ClientService`, `TopupService`, `ApiKeyService`, `SmsService`, `WebhookService`).
**How to avoid:** Add `ApplicationEventPublisher` as a constructor-injected dependency in each
service that needs to emit audit events. Because these services already exist, update their
constructors and callers (tests need to be updated with the additional mock).

### Pitfall 6: Audit event for SMS includes recipient count but not raw phone numbers
**What goes wrong:** AUDT-03 says "client, recipient count, timestamp" — not individual phone numbers.
Storing individual phone numbers in the audit log leaks PII unnecessarily.
**How to avoid:** `DomainAuditEvent` for SMS sends carries `recipientCount` (int), not the phone list.

---

## Code Examples

### Minimal DomainAuditEvent (application event POJO)

```java
// Source: AccountChangeEvent pattern from security/contract/event/
public record DomainAuditEvent(
    AuditEventType eventType,
    Long clientId,          // null for purely admin events with no client target
    String actor,           // admin username or "client:{clientId}" for client-initiated events
    String detail           // free-form, human-readable context
) {}
```

### AuditEventType enum (events to record per requirements)

```java
public enum AuditEventType {
    // AUDT-01: Admin actions on clients
    CLIENT_CREATED,
    TOPUP_APPROVED,
    TOPUP_REJECTED,
    ADMIN_API_KEY_CREATED,
    ADMIN_API_KEY_REVOKED,

    // AUDT-02: Client API key ops (self-service)
    CLIENT_API_KEY_CREATED,
    CLIENT_API_KEY_REVOKED,

    // AUDT-03: SMS send submissions
    SMS_SEND_SUBMITTED,

    // AUDT-04: Webhook config changes
    WEBHOOK_REGISTERED,
    WEBHOOK_UPDATED,
    WEBHOOK_DELETED
}
```

### AuditEventService (REQUIRES_NEW isolation)

```java
// Source: TrailService pattern — security/audit/service/TrailService.java
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public void record(DomainAuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setEventType(event.eventType().name());
        entity.setClientId(event.clientId());
        entity.setActor(event.actor());
        entity.setDetail(event.detail());
        entity.setOccurredAt(Instant.now());
        auditEventRepository.save(entity);
    }
}
```

### AuditEventListener (synchronous, mirrors SecurityAuditListener)

```java
// Source: AccountChangeEventListener pattern — security/audit/listener/
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditEventService auditEventService;

    @EventListener
    public void handle(DomainAuditEvent event) {
        try {
            auditEventService.record(event);
        } catch (Exception e) {
            // Never let audit failure propagate — log and continue
            log.error("Failed to record audit event type={} clientId={}", event.eventType(), event.clientId(), e);
        }
    }
}
```

### Hook injection pattern in existing services

```java
// Example: ClientService after hook injection
@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ApiKeyService apiKeyService;
    private final ClientCreditBalanceRepository clientCreditBalanceRepository;
    private final ApplicationEventPublisher eventPublisher;  // NEW

    public CreateClientResponse createClient(final CreateClientRequest request) {
        // ... existing logic unchanged ...
        eventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.CLIENT_CREATED,
            client.getId(),
            "admin",  // JWT principal resolved from security context
            "Client created: " + request.name()
        ));
        return response;
    }
}
```

### V10 migration SQL

```sql
CREATE TABLE main.audit_event (
    id              BIGINT PRIMARY KEY,               -- @Tsid from BaseEntity
    event_type      VARCHAR(50) NOT NULL,             -- AuditEventType enum name
    client_id       BIGINT REFERENCES main.client_account(id),  -- nullable (admin-only events)
    actor           VARCHAR(100) NOT NULL,            -- who performed the action
    detail          TEXT,                             -- human-readable context
    occurred_at     TIMESTAMP NOT NULL               -- Instant.now() at record time
);

CREATE INDEX idx_audit_event_client_occurred
    ON main.audit_event(client_id, occurred_at DESC)
    WHERE client_id IS NOT NULL;

CREATE INDEX idx_audit_event_occurred
    ON main.audit_event(occurred_at DESC);
```

### Admin resource endpoint

```java
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminAuditResource {

    private final AuditEventService auditEventService;

    @GetMapping("/events")
    public ResponseEntity<Page<AuditEventRow>> getEvents(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "occurred_at", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditEventService.findEvents(clientId, from, to, pageable));
    }
}
```

---

## Trigger Points — Where to Hook

| Requirement | Trigger Point | Service Method | Event Type |
|-------------|---------------|----------------|------------|
| AUDT-01: Client created | `ClientService.createClient` | after save | `CLIENT_CREATED` |
| AUDT-01: Topup approved | `TopupService.approve` | after status update | `TOPUP_APPROVED` |
| AUDT-01: Topup rejected | `TopupService.reject` | after status update | `TOPUP_REJECTED` |
| AUDT-01: Admin creates API key | `AdminApiKeyResource` → `ApiKeyService.createKey` | after key persist | `ADMIN_API_KEY_CREATED` |
| AUDT-01: Admin revokes API key | `AdminApiKeyResource` → `ApiKeyService.revokeKey` | after status update | `ADMIN_API_KEY_REVOKED` |
| AUDT-02: Client creates API key | `ClientApiKeyResource` → `ApiKeyService.createKey` | after key persist | `CLIENT_API_KEY_CREATED` |
| AUDT-02: Client revokes API key | `ClientApiKeyResource` → `ApiKeyService.revokeKey` | after status update | `CLIENT_API_KEY_REVOKED` |
| AUDT-03: SMS send submitted | `SmsService.sendSms` | after `sendRequestRepo.save` | `SMS_SEND_SUBMITTED` |
| AUDT-04: Webhook registered | `WebhookService.register` → `createNew` | after endpoint save | `WEBHOOK_REGISTERED` |
| AUDT-04: Webhook updated | `WebhookService.register` → `updateExisting` | after save | `WEBHOOK_UPDATED` |
| AUDT-04: Webhook deleted | `WebhookService` (if delete exists) / future | after deactivation | `WEBHOOK_DELETED` |

**Note on AUDT-01 admin vs AUDT-02 client API key ops:** `ApiKeyService.createKey` and `revokeKey`
are called from BOTH `AdminApiKeyResource` (admin path) and `ClientApiKeyResource` (client path).
The service cannot intrinsically distinguish which caller invoked it. Two options:

1. **Caller passes actor context:** `createKey(Long clientId, String label, AuditEventType eventType)`
   — resource controller supplies the correct enum value.
2. **Separate service methods:** `adminCreateKey` / `clientCreateKey` — cleaner but duplicates logic.

**Recommended:** Option 1 — the resource controller knows the caller role and passes the correct
`AuditEventType`. The service publishes the event with the supplied type.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Manual pagination (`subList`) | Spring Data `Pageable` + `Page<T>` | SmsService uses old; audit query should use new |
| `Map.of()` for SECURED_MAPPINGS | `Map.ofEntries()` | Mandatory Phase 10 onward; SECURED_MAPPINGS now has 10 entries — 11th requires `ofEntries` |
| Separate `TrailService` for security events | New `AuditEventService` for domain events | Keep both; they serve different purposes |

---

## Open Questions

1. **Webhook DELETE operation (AUDT-04)**
   - What we know: `WebhookService.register` handles upsert (create + update). There is no explicit
     `delete` method in `WebhookService` — the endpoint can be deactivated but the current API does
     not expose a delete endpoint per the v8 contract review.
   - What's unclear: Does AUDT-04 "deletion" refer to a future endpoint, or deactivation (setting
     status to INACTIVE)?
   - Recommendation: Record `WEBHOOK_UPDATED` when status is set to INACTIVE and treat it as
     deletion intent. Add `WEBHOOK_DELETED` type but only emit it if a delete operation exists or
     is added in this phase.

2. **Actor identity in audit events**
   - What we know: Admin events can resolve actor from Spring Security context
     (`SecurityContextHolder.getContext().getAuthentication().getName()`). Client events can use
     `clientId` as the actor.
   - What's unclear: Whether the actor should be the admin's email/username or just "admin".
   - Recommendation: Use `SecurityUtil.getCurrentUserLogin()` (already exists in security module)
     for admin actions; use `"client:" + clientId` for client-initiated actions.

3. **Idempotent send re-submission (AUDT-03)**
   - What we know: `SmsService.sendSms` returns early for duplicate `sendRequestId` (idempotent path)
     without a new DB write.
   - What's unclear: Should the idempotent re-send generate a second audit event?
   - Recommendation: Do NOT audit the idempotent early-return path — only audit the actual first
     submission (the path that creates the `SendRequest` row).

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `security/audit/` module — `TrailService`, `SecurityAuditListener`,
  `AccountChangeEventListener`, `AuditLog`, `AuditTrail`, `AuditTrailMapper`
- Direct code inspection: `common/persistence/` — `BaseEntity`, `AbstractAuditingEntity`
- Direct code inspection: `security/config/AppEndpoints.java` — `SECURED_MAPPINGS` with `Map.ofEntries()`
- Direct code inspection: `gateway/analytics/repo/DeliveryAnalyticsRepository.java` — native query pattern
- Direct code inspection: `gateway/sms/service/SmsFinalisedListener.java` — `@TransactionalEventListener` + `REQUIRES_NEW`
- Direct code inspection: `gateway/webhook/service/WebhookService.java` — upsert pattern, ApplicationEventPublisher
- Direct code inspection: `gateway/billing/service/TopupService.java`, `gateway/account/service/ClientService.java`
- Direct code inspection: V5–V9 Flyway migrations — schema conventions

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already present; patterns verified from source
- Architecture: HIGH — new module mirrors established pattern; no novel choices required
- Pitfalls: HIGH — derived from documented invariants in STATE.md + code inspection
- Trigger points: HIGH — service methods identified by direct reading; one open question on delete
- Pagination pattern: HIGH — `JpaRepository` + native `Pageable` is Spring Data documented feature

**Research date:** 2026-03-11
**Valid until:** 60 days (stable codebase, no external dependency changes needed)
