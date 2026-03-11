# Phase 8: Delivery Analytics (Admin) - Research

**Researched:** 2026-03-11
**Domain:** Read-only aggregation queries over existing SMS tables; new admin REST endpoint
**Confidence:** HIGH (all findings sourced directly from codebase)

---

## Summary

Phase 8 requires three admin analytics endpoints that aggregate data from two existing
tables: `send_request` and `send_request_recipient`. No schema changes are needed. All data
already exists in the right shape — the delivery lifecycle columns (`send_status` on both
tables, `segments_consumed` on recipients, `finalized_at` on send_request) provide everything
required for sent/delivered/failed counts, delivery rate, segment totals, and a daily breakdown.

The standard approach is a new `gateway/analytics` module following the project's existing
layered pattern (`api`, `service`, `repo`, `contract`), with a single new JPA repository
using `@Query` JPQL/native SQL for the aggregation queries, a service that maps results into
response records, and a controller mounted at `/api/admin/analytics/**` registered in
`AppEndpoints` and secured via the existing JWT filter chain.

**Primary recommendation:** Build a self-contained `gateway/analytics` module. Do not add
analytics methods to existing `SendRequestRepository` or `SendRequestRecipientRepository` —
those repositories are owned by the `sms` domain and should not accumulate cross-cutting
concerns.

---

## Standard Stack

This phase uses no new libraries. Everything is already on the classpath.

### Core
| Library | Purpose | Why Standard |
|---------|---------|--------------|
| Spring Data JPA | Repository and `@Query` for JPQL/native aggregation | Already used across every module |
| Spring MVC `@RestController` | New admin controller | Identical to `AdminClientResource`, `AdminTopupResource` |
| Java records | Response DTOs | Consistent with `BalanceResponse`, `LedgerHistoryResponse`, `AdminClientDto` |
| `@JsonProperty` | Snake-case JSON field naming | Consistent with `LedgerEntryDto`, `BalanceResponse` |

### No new dependencies required.

---

## Architecture Patterns

### Recommended Module Structure

```
gateway/analytics/
├── api/
│   └── AdminDeliveryAnalyticsResource.java   # @RestController, /api/admin/analytics/**
├── contract/
│   ├── DeliveryStatsResponse.java             # summary + daily breakdown (DANL-01, DANL-03)
│   ├── DeliveryDailyStat.java                 # one bucket per day in the window
│   └── SegmentTotalsResponse.java             # segment totals (DANL-02)
├── repo/
│   └── DeliveryAnalyticsRepository.java       # interface with @Query methods
└── service/
    └── DeliveryAnalyticsService.java          # maps raw results to response records
```

This follows the identical pattern established for all gateway modules.

### Pattern 1: Aggregation Repository with @Query

For summary totals, use JPQL `COUNT` / `SUM` with optional filters. JPQL supports
`COUNT(CASE WHEN ...)` via native SQL or separate named queries.

The cleanest approach for multi-conditional counts is a **native SQL `@Query`** with
`nativeQuery = true`. JPQL `SUM(CASE WHEN ...)` is not portable in all Hibernate versions
and `COUNT(CASE WHEN ...)` requires casting. Native SQL is direct and unambiguous.

**For summary totals (DANL-01 + DANL-02), query on `send_request_recipient`:**

```sql
-- Source: V5__send_request.sql + V6__send_request_finalized_at.sql
SELECT
    COUNT(*)                                          AS total_sent,
    COUNT(CASE WHEN r.send_status = 'FINALIZED'
               THEN 1 END)                            AS delivered,
    COUNT(CASE WHEN r.send_status = 'FAIL_FINALIZED'
               THEN 1 END)                            AS failed,
    COALESCE(SUM(r.segments_consumed), 0)             AS total_segments
FROM main.send_request_recipient r
WHERE (:clientId IS NULL OR r.client_id = :clientId)
  AND (:from IS NULL    OR r.created_date >= :from)
  AND (:to   IS NULL    OR r.created_date <= :to)
```

Note: `FINALIZED` and `FAIL_FINALIZED` are the terminal states on `send_request_recipient`
(same `SendRequestStatus` enum used on both parent and child entities). These are the
authoritative "delivered" and "failed" states — all prior states (ACCEPTED, SUBMITTED,
COMPLETED, FAILED) are transitional.

**For daily breakdown (DANL-03), use `DATE_TRUNC` on `created_date`:**

```sql
SELECT
    DATE_TRUNC('day', r.created_date)                 AS day,
    COUNT(*)                                          AS total_sent,
    COUNT(CASE WHEN r.send_status = 'FINALIZED'
               THEN 1 END)                            AS delivered,
    COUNT(CASE WHEN r.send_status = 'FAIL_FINALIZED'
               THEN 1 END)                            AS failed,
    COALESCE(SUM(r.segments_consumed), 0)             AS total_segments
FROM main.send_request_recipient r
WHERE (:clientId IS NULL OR r.client_id = :clientId)
  AND (:from IS NULL    OR r.created_date >= :from)
  AND (:to   IS NULL    OR r.created_date <= :to)
GROUP BY DATE_TRUNC('day', r.created_date)
ORDER BY day
```

`DATE_TRUNC` is standard PostgreSQL — correct for this project's DB.

### Pattern 2: Repository Return Type for Native Query Projections

Native queries returning multiple aggregate columns cannot map to a JPA entity. Use a
**Spring Data projection interface** (closed projection) in `contract/` — consistent with
the project rule that projection interfaces live in `contract/`, not in `repo/`.

```java
// contract/DeliveryStatRow.java  (projection interface)
public interface DeliveryStatRow {
    long getTotalSent();
    long getDelivered();
    long getFailed();
    long getTotalSegments();
}

// contract/DeliveryDailyStatRow.java  (projection interface for daily breakdown)
public interface DeliveryDailyStatRow {
    java.time.LocalDate getDay();    // DATE_TRUNC returns timestamp; cast to date in SQL or convert in service
    long getTotalSent();
    long getDelivered();
    long getFailed();
    long getTotalSegments();
}
```

Alternatively, use a `@SqlResultSetMapping` + `@NamedNativeQuery`, but Spring Data
projections are lighter and the existing codebase uses `AdminClientDto` constructor
expressions (JPQL) and plain entity returns — projections are the next natural step for
native queries.

### Pattern 3: Query Parameters as Optional Filters

`clientId`, `from`, and `to` are all optional. The `:clientId IS NULL OR ...` pattern
in native SQL handles this in a single query. In the repository method signature:

```java
@Query(value = "...", nativeQuery = true)
DeliveryStatRow findDeliveryStats(
    @Param("clientId") Long clientId,    // null = all clients
    @Param("from")     Instant from,     // null = no lower bound
    @Param("to")       Instant to        // null = no upper bound
);
```

### Pattern 4: Admin Endpoint Registration

New admin analytics paths must be registered in `AppEndpoints` and the JWT
`SecurityConfiguration` SECURED_MAPPINGS, exactly as done for `ADMIN_CLIENTS`,
`ADMIN_TOPUPS`, and `ADMIN_API_KEYS`.

```java
// In AppEndpoints:
public static final String ADMIN_ANALYTICS = "/api/admin/analytics/**";

// In SECURED_MAPPINGS static block:
ADMIN_ANALYTICS, new String[]{AuthoritiesConstants.ADMIN}
```

The controller mounts at `/api/admin/analytics` — under `/api/**` which is the JWT chain
scope. No `@PreAuthorize` is strictly needed (filter chain enforces it), but `AdminTopupResource`
uses belt-and-suspenders `@PreAuthorize("hasRole('ADMIN')")`. Either approach is acceptable;
be consistent with the existing admin resources.

### Pattern 5: Response Shape Convention

Existing admin responses use Java records with `@JsonProperty` for snake_case. Follow:

```java
// Summary + daily list combined in one response (satisfies DANL-01 + DANL-03 in one call)
public record DeliveryStatsResponse(
    @JsonProperty("total_sent")      long totalSent,
    @JsonProperty("delivered")       long delivered,
    @JsonProperty("failed")          long failed,
    @JsonProperty("delivery_rate")   double deliveryRate,    // delivered / totalSent * 100, or 0 if totalSent=0
    @JsonProperty("daily_breakdown") List<DeliveryDailyStat> dailyBreakdown
) {}

public record DeliveryDailyStat(
    @JsonProperty("date")            String date,            // ISO-8601 date string, e.g. "2026-03-01"
    @JsonProperty("total_sent")      long totalSent,
    @JsonProperty("delivered")       long delivered,
    @JsonProperty("failed")          long failed
) {}

// Segment totals response (satisfies DANL-02)
public record SegmentTotalsResponse(
    @JsonProperty("total_segments")  long totalSegments,
    @JsonProperty("client_id")       Long clientId,          // null means all clients
    @JsonProperty("from")            Instant from,
    @JsonProperty("to")              Instant to
) {}
```

### Anti-Patterns to Avoid

- **Adding analytics queries to `SendRequestRepository`:** That repository is owned by the
  `sms` domain. Cross-cutting concerns like analytics belong in a dedicated analytics module.
- **Re-querying recipient table twice** (once for summary, once for daily): A single
  query with both aggregate and `GROUP BY` day can return both. Alternatively, one summary
  query + one daily query is also fine since these are read-only and low-frequency admin calls.
- **Materialised views or caching:** The v1.1 constraint is "live aggregation queries on
  existing tables — no pre-aggregation/materialized tables unless query performance requires it."
  Do not add any scheduler, cache, or materialised table unless a measured performance problem
  exists.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Optional filter in JPQL/SQL | Custom filter-building logic | `:param IS NULL OR col = :param` in native query | One query handles all filter combinations |
| Delivery rate calculation | Complex domain service | Simple arithmetic in service layer: `delivered * 100.0 / max(1, totalSent)` | No precision issues; division-by-zero guard sufficient |
| Date bucketing | Java-side grouping on loaded rows | `DATE_TRUNC('day', ...)` in SQL | Never load all rows to group in Java |
| Response serialisation | Custom serialiser | `@JsonProperty` on record fields | Already the project pattern |

---

## Common Pitfalls

### Pitfall 1: Counting "sent" on the wrong table/column

**What goes wrong:** Counting `send_request.send_status` instead of
`send_request_recipient.send_status`. A single `send_request` with 100 recipients is one
row in `send_request` but 100 rows in `send_request_recipient`.

**Why it happens:** `send_request` is the entity developers see first.

**How to avoid:** All message-level counts (sent, delivered, failed) must be derived from
`send_request_recipient` — each row is one message to one recipient.

**Warning signs:** Count totals match the number of send requests, not recipients.

### Pitfall 2: Treating COMPLETED/FAILED as terminal states

**What goes wrong:** Counting `COMPLETED` as "delivered" or `FAILED` as "failed". These
are intermediate states in the delivery state machine.

**Why it happens:** The state machine has two failure states and two success states.

**Correct mapping:**
- `FINALIZED` = delivered (terminal success — provider confirmed delivery)
- `FAIL_FINALIZED` = failed (terminal failure — credit settled at fail-settle amount)
- `SUBMITTED`, `COMPLETED`, `FAILED`, `ACCEPTED` = in-flight (do not count as terminal)
- `CANCELLED` = cancelled before delivery (do not count as sent to provider)

**Warning signs:** delivered + failed counts do not add up to total_sent when most messages
have reached a terminal state.

### Pitfall 3: Division by zero in delivery rate

**What goes wrong:** `delivered / totalSent` throws ArithmeticException (integer division)
or returns NaN/Infinity (double division) when `totalSent` is 0.

**How to avoid:** Guard: `totalSent == 0 ? 0.0 : (double) delivered / totalSent * 100`.

### Pitfall 4: Missing AppEndpoints / SecurityConfiguration registration

**What goes wrong:** The new `/api/admin/analytics/**` path is not added to `AppEndpoints`
or the `SECURED_MAPPINGS`, so Spring Security either blocks it (returns 403) or falls
through to an unprotected handler.

**How to avoid:** New admin paths MUST be added to both `AppEndpoints` (constant + mapping
entry) and verified in `SecurityConfiguration` SECURED_MAPPINGS. The existing three admin
path constants (`ADMIN_CLIENTS`, `ADMIN_TOPUPS`, `ADMIN_API_KEYS`) demonstrate the pattern.

### Pitfall 5: `created_date` timezone handling

**What goes wrong:** `DATE_TRUNC('day', created_date)` truncates to UTC day boundaries.
If the admin expects local-time day buckets, the result will appear shifted.

**How to avoid:** For v1.1, UTC day bucketing is appropriate (all timestamps are stored as
`TIMESTAMP` without timezone, written by the JVM's clock). Document this assumption. Do not
introduce timezone conversion unless explicitly required.

### Pitfall 6: segments_consumed NULL for in-flight recipients

**What goes wrong:** `SUM(segments_consumed)` silently omits in-flight recipients (where
`segments_consumed IS NULL`). This is intentional — only finalized/fail-finalized rows have
a confirmed segment count. `COALESCE(SUM(...), 0)` handles the all-null case.

**How to avoid:** Use `COALESCE(SUM(r.segments_consumed), 0)` in the query. Document that
segment totals reflect only settled messages, not in-flight ones.

---

## Code Examples

### Existing Native JPQL Pattern — AdminClientDto Constructor Expression

```java
// Source: gateway/account/repo/ClientRepository.java
@Query("SELECT new com.softropic.sendam.gateway.account.contract.AdminClientDto(c.id, c.name, c.status, COALESCE(b.balance, 0L)) " +
       "FROM ClientEntity c LEFT JOIN ClientCreditBalance b ON b.clientId = c.id " +
       "ORDER BY c.createdDate DESC")
List<AdminClientDto> findAllWithBalance();
```

This is the most complex existing query. Analytics queries will use native SQL instead
(for `DATE_TRUNC` and `CASE WHEN` aggregation) and projection interfaces as return types.

### Existing Optional @RequestParam Pattern — CreditResource

```java
// Source: gateway/billing/api/CreditResource.java
@GetMapping("/ledger")
public ResponseEntity<LedgerHistoryResponse> getLedgerHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
```

Analytics endpoints use optional params without defaults (null = no filter):

```java
@GetMapping("/delivery-stats")
public ResponseEntity<DeliveryStatsResponse> getDeliveryStats(
        @RequestParam(required = false) Long clientId,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
```

### Existing Admin Auth Pattern — AdminTopupResource

```java
// Source: gateway/billing/api/AdminTopupResource.java
@RestController
@RequestMapping("/api/admin/topups")
@RequiredArgsConstructor
@Slf4j
public class AdminTopupResource {

    @PutMapping("/{topup_id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopupStatusResponse> approve(...)
```

New analytics resource follows the same mount-point convention and optional belt-and-suspenders
`@PreAuthorize`.

---

## Key Data Model Facts (HIGH confidence — sourced from migrations + entities)

### send_request table (one row per send call)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (TSID) | PK |
| client_id | BIGINT | FK to client_account |
| send_status | VARCHAR(30) | SendRequestStatus enum |
| message_count | INT | number of recipients in original request |
| segment_count | INT | estimated segments at reservation time |
| reserved_credits | BIGINT | credits reserved at send time |
| finalized_at | TIMESTAMP | set when terminal state reached |
| created_date | TIMESTAMP | from AbstractAuditingEntity; set at request creation |

### send_request_recipient table (one row per message = per recipient)
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT (TSID) | PK |
| send_request_id_fk | BIGINT | FK to send_request |
| client_id | BIGINT | FK to client_account (denormalized for index efficiency) |
| send_status | VARCHAR(30) | SendRequestStatus enum (same enum, per-recipient lifecycle) |
| segments_consumed | INT | NULL until finalized; actual provider-confirmed segments |
| created_date | TIMESTAMP | from AbstractAuditingEntity |

### SendRequestStatus enum (terminal states for analytics)
```
ACCEPTED      → submitted to queue, not yet sent to provider
SUBMITTED     → dispatched to Nexah, awaiting DR callback
COMPLETED     → DR callback received as delivered (intermediate)
FAILED        → DR callback received as failed (intermediate)
FINALIZED     → TERMINAL SUCCESS — ledger settled, delivery confirmed
FAIL_FINALIZED → TERMINAL FAILURE — ledger settled at fail amount
CANCELLED     → cancelled by client before dispatch
```

For analytics: **count `FINALIZED` as delivered, `FAIL_FINALIZED` as failed**.
Do not include `CANCELLED` in "sent" totals (the message was never dispatched to provider).

### Existing index on recipients
```sql
CREATE INDEX idx_send_request_recipient_client ON main.send_request_recipient(client_id, send_status);
```
This index supports filtered aggregation by `client_id`. For time-range queries, a composite
index on `(client_id, created_date)` would help. The planner should decide whether to add
a migration for this or defer it.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|-----------------|--------|
| N/A — no analytics yet | New read-only aggregation queries | Phase adds analytics without touching existing domain logic |

No deprecated patterns apply to this phase.

---

## Open Questions

1. **Should `CANCELLED` recipients count toward `total_sent`?**
   - What we know: `CANCELLED` is a terminal state set when the client cancels a scheduled
     request before dispatch. Credits are released. The message was never sent to Nexah.
   - Recommendation: Exclude `CANCELLED` from all counts. Only count recipients that were
     actually submitted to the provider (`SUBMITTED` through `FAIL_FINALIZED`). This means
     `total_sent` = count of non-CANCELLED, non-ACCEPTED rows, or more precisely, count
     of rows that reached `FINALIZED` or `FAIL_FINALIZED`. The planner should confirm which
     "sent" definition is correct: all rows where dispatch was attempted, or only terminal
     rows.

2. **Time filter parameter format**
   - What we know: The project uses `Instant` timestamps throughout. `@RequestParam` with
     `@DateTimeFormat(iso = ISO.DATE_TIME)` parses ISO-8601.
   - What's unclear: Should the API accept ISO-8601 datetime strings (e.g.
     `2026-03-01T00:00:00Z`) or just dates (`2026-03-01`)? Datetime is more precise but
     less ergonomic for admin use.
   - Recommendation: Accept ISO-8601 datetime (Instant). If date-only is preferred,
     accept `LocalDate` and convert to `Instant` at start-of-day UTC in the service.

3. **Performance index for time-range queries on recipients**
   - What we know: Existing index is `(client_id, send_status)`. No index on
     `(created_date)` for the recipient table.
   - What's unclear: Whether a new index on `(client_id, created_date)` should be part
     of this phase or deferred.
   - Recommendation: Add a Flyway migration `V8__analytics_index.sql` with
     `CREATE INDEX idx_srr_client_date ON main.send_request_recipient(client_id, created_date)`
     as part of this phase. For admin-only, low-frequency queries, it is optional but clean.

4. **Combining DANL-01 and DANL-02 into one endpoint or two**
   - What we know: DANL-01 is delivery counts+rate, DANL-02 is segment totals.
     Both use the same filters and the same base table.
   - Recommendation: One endpoint (`GET /api/admin/analytics/delivery-stats`) returning a
     combined response that includes both count metrics and segment totals. This halves the
     number of round trips and the SQL queries execute the same aggregation anyway. DANL-03
     (daily breakdown) can be included in the same response or as a separate
     `?breakdown=true` parameter.

---

## Sources

### Primary (HIGH confidence — codebase)
- `src/main/resources/db/migration/V5__send_request.sql` — exact table DDL
- `src/main/resources/db/migration/V6__send_request_finalized_at.sql` — finalized_at column
- `gateway/sms/repo/SendRequest.java` — entity fields and column names
- `gateway/sms/repo/SendRequestRecipient.java` — recipient entity, segments_consumed
- `gateway/sms/contract/SendRequestStatus.java` — complete enum with all states
- `gateway/account/repo/ClientRepository.java` — JPQL constructor expression pattern
- `gateway/billing/api/CreditResource.java` — optional @RequestParam pattern
- `gateway/billing/api/AdminTopupResource.java` — admin auth pattern
- `gateway/account/api/AdminClientResource.java` — admin controller pattern
- `security/config/AppEndpoints.java` — how admin paths are registered
- `gateway/auth/config/ClientSecurityConfiguration.java` — dual filter chain

---

## Metadata

**Confidence breakdown:**
- Table structure and column names: HIGH — read directly from migration SQL and entities
- SendRequestStatus semantics (FINALIZED = delivered): HIGH — from entity JavaDoc and migration
- Admin endpoint pattern (path, auth, response shape): HIGH — three existing examples
- SQL aggregation approach (native query + projection): HIGH — Spring Data JPQL/native patterns are stable
- Performance (whether index needed): MEDIUM — depends on data volume; no benchmarks available
- CANCELLED vs non-CANCELLED definition of "sent": MEDIUM — semantics clear, but product intent unconfirmed

**Research date:** 2026-03-11
**Valid until:** Stable until schema migration changes (at least 90 days)
