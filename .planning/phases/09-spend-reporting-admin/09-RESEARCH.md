# Phase 9: Spend Reporting (Admin) - Research

**Researched:** 2026-03-11
**Domain:** Spring Data JPA aggregation queries over existing ledger and topup tables
**Confidence:** HIGH

## Summary

Phase 9 adds two read-only admin endpoints that aggregate data from two already-existing tables:
`main.credit_ledger_entry` (for net-spend breakdown per client per period) and
`main.topup_request` (for top-up history per client per period). No new Flyway migrations are
required for the data model, though an optional index migration may be added for query
performance.

The Phase 8 delivery analytics implementation is an exact structural template. Phase 9 follows
the same four-layer pattern: projection interface (contract/) → Repository<Object, Long> with
native `@Query` → `@Transactional(readOnly = true)` service → admin `@RestController` at
`/api/admin/spend`. Security registration in `AppEndpoints` is also required.

There is no existing admin spend endpoint. This is entirely net-new code inside the existing
`gateway/billing` module package tree (or a new `gateway/spend` submodule — see Architecture
Patterns).

**Primary recommendation:** Follow Phase 8's structure verbatim. One pure-aggregation
repository per data source (ledger / topup), one service, one admin controller. Add an
`ADMIN_SPEND` constant to `AppEndpoints` and register it in `SECURED_MAPPINGS`.

---

## Standard Stack

### Core (all already in pom.xml / classpath)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | (boot-managed) | Repository abstraction | Project-wide standard |
| Hibernate (native query) | (boot-managed) | `nativeQuery = true` aggregate queries | Required for SQL `GROUP BY` on non-entity |
| Spring MVC | (boot-managed) | `@RestController`, `@GetMapping` | Project-wide standard |
| Spring Security | (boot-managed) | `@PreAuthorize("hasRole('ADMIN')")` | Belt-and-suspenders admin auth |
| Lombok | (boot-managed) | `@RequiredArgsConstructor`, `@Slf4j` | Project-wide standard |
| Jackson | (boot-managed) | `@JsonProperty` on response records | Project-wide standard |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.sql.Date` | JDK | Return type for `DATE_TRUNC` projection getter | Only for `getDay()` in daily-breakdown projections; convert to `LocalDate` in service |
| `java.time.Instant` | JDK | Filter params `from`/`to` | All timestamp filter params in this project |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Repository<Object, Long>` | `JpaRepository<TopupRequestEntity, Long>` | `JpaRepository` already exists for `TopupRequestEntity`; for pure aggregation the project convention is `Repository<Object, Long>` — no entity binding. Use a second interface (e.g. `SpendLedgerRepository`) extending `Repository<Object, Long>` for aggregations |
| New `gateway/spend` package | Reuse `gateway/billing` | Either is valid. Billing is already large; a `gateway/spend` module mirrors the `gateway/analytics` precedent and keeps concerns separated |

**Installation:** No new dependencies needed.

---

## Architecture Patterns

### Recommended Project Structure

New files follow `gateway/analytics` as exact template:

```
src/main/java/com/softropic/sendam/gateway/spend/
├── api/
│   └── AdminSpendResource.java          # @RestController /api/admin/spend
├── contract/
│   ├── SpendSummaryRow.java             # projection interface — ledger aggregate
│   ├── SpendBreakdownRow.java           # projection interface — ledger per entry_type
│   ├── SpendSummaryResponse.java        # record returned by /spend/credits
│   ├── TopupHistoryRow.java             # projection interface — topup_request rows
│   └── TopupHistoryResponse.java        # record returned by /spend/topups
├── repo/
│   └── SpendRepository.java            # Repository<Object, Long>, native @Query
└── service/
    └── SpendService.java               # @Transactional(readOnly = true), mapping logic
```

Flyway (optional but recommended for performance):
```
src/main/resources/db/migration/V9__spend_index.sql
```

### Pattern 1: Pure-Aggregation Repository (Repository<Object, Long>)

**What:** A Spring Data interface that extends `Repository<Object, Long>` rather than
`JpaRepository`. No entity is bound. Used exclusively for native aggregate queries that
return projections.

**When to use:** Whenever the query produces a result set that does not map to a single
entity — e.g., `SUM`, `COUNT`, `GROUP BY entry_type`.

```java
// Source: gateway/analytics/repo/DeliveryAnalyticsRepository.java (Phase 8 reference)
@org.springframework.stereotype.Repository
public interface SpendRepository extends Repository<Object, Long> {

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN e.entry_type IN ('SMS_DEBIT','SMS_REFUND','TOPUP_PENDING','TOPUP_APPROVED')
                               THEN e.amount ELSE 0 END), 0) AS net_spend,
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_DEBIT'
                               THEN e.amount ELSE 0 END), 0) AS sms_debit,
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_REFUND'
                               THEN e.amount ELSE 0 END), 0) AS sms_refund,
            COALESCE(SUM(CASE WHEN e.entry_type = 'TOPUP_APPROVED'
                               THEN e.amount ELSE 0 END), 0) AS topup_approved,
            COALESCE(SUM(CASE WHEN e.entry_type = 'TOPUP_PENDING'
                               THEN e.amount ELSE 0 END), 0) AS topup_pending,
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_RESERVATION'
                               THEN e.amount ELSE 0 END), 0) AS sms_reservation
        FROM main.credit_ledger_entry e
        WHERE (:clientId IS NULL OR e.client_id = :clientId)
          AND (:from IS NULL OR e.created_date >= :from)
          AND (:to   IS NULL OR e.created_date <= :to)
        """, nativeQuery = true)
    SpendSummaryRow findSpendSummary(
        @Param("clientId") Long clientId,
        @Param("from")     Instant from,
        @Param("to")       Instant to
    );
}
```

### Pattern 2: Projection Interface

**What:** A plain Java interface whose getter names are matched by column aliases in the
native query result. Spring Data wires the result set columns to the getter names
automatically (case-insensitive match on alias).

**When to use:** Every native aggregate query result.

```java
// Source: gateway/analytics/contract/DeliveryStatRow.java (Phase 8 reference)
public interface SpendSummaryRow {
    long getNetSpend();
    long getSmsDebit();
    long getSmsRefund();
    long getTopupApproved();
    long getTopupPending();
    long getSmsReservation();
}
```

**Critical rule:** Getter name maps to SQL column alias via Spring Data's camelCase-to-
underscore conversion. `getNetSpend()` maps to alias `net_spend`. Mismatch → all zeros.

### Pattern 3: DATE_TRUNC daily breakdown (if required)

If SPEN-01 or SPEN-02 need a per-day breakdown of spend, follow Phase 8's
`findDailyBreakdown` pattern exactly:

```java
// Source: gateway/analytics/repo/DeliveryAnalyticsRepository.java (Phase 8 reference)
@Query(value = """
    SELECT
        DATE_TRUNC('day', e.created_date)::date AS day,
        ...
    FROM main.credit_ledger_entry e
    WHERE ...
    GROUP BY DATE_TRUNC('day', e.created_date)::date
    ORDER BY day
    """, nativeQuery = true)
List<SpendDailyRow> findDailySpend(...);
```

Return type for `getDay()` in the projection interface MUST be `java.sql.Date` (not
`LocalDate`). Convert to `LocalDate` in the service with `row.getDay().toLocalDate()`.

### Pattern 4: Top-up History Query

For SPEN-03, the `topup_request` table is already served by `JpaRepository<TopupRequestEntity, Long>`.
Two options:

**Option A (recommended):** Add new native aggregate query methods to `SpendRepository`
(the new `Repository<Object, Long>`) — keeps the new phase self-contained.

**Option B:** Add query methods to the existing `TopupRequestRepository extends JpaRepository`.
This works but mixes lifecycle-write concerns with read-only reporting. Avoid.

The topup history query for admin needs `clientId` + optional `topupStatus` + date range:

```sql
SELECT
    t.id,
    t.client_id,
    t.amount,
    t.transaction_id,
    t.payment_type,
    t.account_number,
    t.topup_status,
    t.created_date,
    t.approved_at,
    t.rejected_at
FROM main.topup_request t
WHERE (:clientId IS NULL OR t.client_id = :clientId)
  AND (:topupStatus IS NULL OR t.topup_status = :topupStatus)
  AND (:from IS NULL OR t.created_date >= :from)
  AND (:to   IS NULL OR t.created_date <= :to)
ORDER BY t.created_date DESC
```

### Pattern 5: Admin Controller Security

**What:** Two layers — filter chain via `AppEndpoints.SECURED_MAPPINGS` (path-level) plus
`@PreAuthorize("hasRole('ADMIN')")` per method (belt-and-suspenders). This matches all
existing admin controllers exactly.

```java
// Source: gateway/analytics/api/AdminDeliveryAnalyticsResource.java (Phase 8 reference)
@RestController
@RequestMapping("/api/admin/spend")
@RequiredArgsConstructor
@Slf4j
public class AdminSpendResource {

    private final SpendService spendService;

    @GetMapping("/credits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpendSummaryResponse> getSpendSummary(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(spendService.getSpendSummary(clientId, from, to));
    }

    @GetMapping("/topups")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopupHistoryResponse> getTopupHistory(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String topupStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(spendService.getTopupHistory(clientId, topupStatus, from, to));
    }
}
```

### Pattern 6: AppEndpoints registration

Add `ADMIN_SPEND` to `AppEndpoints` and register it in `SECURED_MAPPINGS`. The existing
`SECURED_MAPPINGS` `Map.of()` call has exactly 8 entries (Map.of max is 10); adding one more
entry is safe. Verify count before adding.

```java
// Source: security/config/AppEndpoints.java
public static final String ADMIN_SPEND = "/api/admin/spend/**";
// ...in static block:
SECURED_MAPPINGS = Map.of(
    SECURED,          SECURED_AUTHORITIES,
    SECURED_API,      SECURED_AUTHORITIES,
    ACTUATOR,         new String[]{AuthoritiesConstants.ADMIN},
    REFRESH,          SECURED_AUTHORITIES,
    ADMIN_CLIENTS,    new String[]{AuthoritiesConstants.ADMIN},
    ADMIN_TOPUPS,     new String[]{AuthoritiesConstants.ADMIN},
    ADMIN_API_KEYS,   new String[]{AuthoritiesConstants.ADMIN},
    ADMIN_ANALYTICS,  new String[]{AuthoritiesConstants.ADMIN},
    ADMIN_SPEND,      new String[]{AuthoritiesConstants.ADMIN}   // NEW — but Map.of() max is 10!
);
```

**WARNING:** `Map.of()` accepts at most 10 key-value pairs. Current `SECURED_MAPPINGS` has
8 entries. Adding `ADMIN_SPEND` brings it to 9. Still safe. If a tenth admin path is added
in future, switch to `Map.ofEntries(...)`.

### Anti-Patterns to Avoid

- **Hand-rolling null-safe amount summation in Java:** Use `COALESCE(SUM(...), 0)` in SQL
  instead. Projections return `long`, which avoids null handling in the service entirely.
- **Loading all ledger rows and aggregating in-service:** The ledger can be large. Always
  push aggregation to the database via native `@Query`.
- **Using `JpaRepository<CreditLedgerEntry, Long>` for aggregation:** The existing
  `CreditLedgerRepository` extends `JpaRepository` for entity reads. New aggregation queries
  for spend reporting belong in a separate `Repository<Object, Long>` interface to keep
  concerns separated and avoid polluting the write-path repository.
- **Binding `TopupStatus` enum as a Spring Data param in native query:** Native queries
  receive enum values as Strings from Spring Data. Use `String topupStatus` as the param type
  and filter with `t.topup_status = :topupStatus`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Null-safe SUM on empty result set | Java Optional + null check | `COALESCE(SUM(...), 0)` in SQL | DB returns one row even when no data; COALESCE ensures 0 not null |
| Projection result mapping | Custom `ResultTransformer` | Spring Data projection interface | Spring Data wires alias → getter automatically |
| Admin route protection | Custom filter logic | `AppEndpoints` constant + `SECURED_MAPPINGS` + `@PreAuthorize` | Established project pattern, consistent with all other admin endpoints |
| Date parsing for filter params | Manual string parsing | `@DateTimeFormat(iso = ISO.DATE_TIME)` on `Instant` param | Handles ISO-8601 via Spring MVC automatically |

**Key insight:** The entire pattern (projection interface + pure-aggregation repository +
readOnly service + admin controller) is established in Phase 8. Copy structure; change only
the SQL, field names, and response shapes.

---

## Common Pitfalls

### Pitfall 1: Projection getter name vs SQL alias mismatch

**What goes wrong:** Query returns all-zero values or throws binding exception.
**Why it happens:** Spring Data maps SQL column aliases to getter names via camelCase
conversion. `getSmsDebit()` requires alias `sms_debit`. If the alias is `sms_debits` (plural)
or `smsDEBIT`, the binding fails silently and returns 0.
**How to avoid:** Write the SQL alias first, then derive the getter name from it exactly.
**Warning signs:** All numeric fields in the response are 0; no exception thrown.

### Pitfall 2: `Map.of()` entry limit

**What goes wrong:** `IllegalArgumentException: duplicate key` or compile error if more than
10 entries are added to `Map.of()` in `AppEndpoints`.
**Why it happens:** `Map.of(K, V, ...)` overloads only go up to 10 pairs.
**How to avoid:** Count current entries (currently 8) before adding. Phase 9 adds 1 → 9
total, which is within limit.
**Warning signs:** Compile error or test failure on `AppEndpoints` static initializer.

### Pitfall 3: Reusing TopupRequestRepository for admin read queries

**What goes wrong:** Write-path repository becomes bloated with reporting-only query
methods; later phases suffer.
**Why it happens:** `TopupRequestRepository` already holds the entity type and seems
convenient.
**How to avoid:** Create `SpendRepository extends Repository<Object, Long>` for all
aggregation queries, per the project convention established in Phase 8.

### Pitfall 4: Querying topup_request by date without an index

**What goes wrong:** Slow queries as the table grows.
**Why it happens:** `idx_topup_client_status` (V4) indexes `(client_id, topup_status)`.
A date-range query on `created_date` with optional `clientId` filter will do a full scan.
**How to avoid:** Add `V9__spend_index.sql` with:
```sql
CREATE INDEX idx_topup_client_date ON main.topup_request(client_id, created_date DESC);
```
The `credit_ledger_entry` table already has `idx_ledger_client_date` on
`(client_id, created_date DESC)` from V3 — no new index needed for the ledger.

### Pitfall 5: `java.sql.Date` vs `LocalDate` confusion in projections

**What goes wrong:** Hibernate cannot map `DATE_TRUNC(...)::date` result to `LocalDate`
getter; throws `ConversionException` or returns null.
**Why it happens:** Hibernate's native query result mapping uses `java.sql.Date` for SQL
`DATE` type.
**How to avoid:** Use `java.sql.Date getDay()` in the projection interface (as in Phase 8's
`DeliveryDailyStatRow`). Convert to `LocalDate` in the service: `row.getDay().toLocalDate()`.

---

## Code Examples

### Net-spend summary (SPEN-01 + SPEN-02) — SQL

```sql
-- Source: derived from Phase 8 DeliveryAnalyticsRepository pattern
SELECT
    COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_DEBIT'       THEN ABS(e.amount) ELSE 0 END), 0) AS sms_debit,
    COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_REFUND'      THEN e.amount      ELSE 0 END), 0) AS sms_refund,
    COALESCE(SUM(CASE WHEN e.entry_type = 'TOPUP_APPROVED'  THEN e.amount      ELSE 0 END), 0) AS topup_approved,
    COALESCE(SUM(CASE WHEN e.entry_type = 'TOPUP_PENDING'   THEN e.amount      ELSE 0 END), 0) AS topup_pending,
    COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_RESERVATION' THEN ABS(e.amount) ELSE 0 END), 0) AS sms_reservation
FROM main.credit_ledger_entry e
WHERE (:clientId IS NULL OR e.client_id = :clientId)
  AND (:from IS NULL OR e.created_date >= :from)
  AND (:to   IS NULL OR e.created_date <= :to)
```

Note: `CreditLedgerEntry.amount` is signed (positive = credit, negative = debit).
`SMS_DEBIT` and `SMS_RESERVATION` store negative amounts. Use `ABS()` to report
positive consumption figures — or let the service negate the value. Be explicit in the
response contract which sign convention is used.

### Top-up history (SPEN-03) — SQL

```sql
-- Source: derived from TopupRequestEntity field inspection
SELECT
    t.id              AS id,
    t.client_id       AS client_id,
    t.amount          AS amount,
    t.transaction_id  AS transaction_id,
    t.payment_type    AS payment_type,
    t.account_number  AS account_number,
    t.topup_status    AS topup_status,
    t.created_date    AS created_date,
    t.approved_at     AS approved_at,
    t.rejected_at     AS rejected_at
FROM main.topup_request t
WHERE (:clientId    IS NULL OR t.client_id    = :clientId)
  AND (:topupStatus IS NULL OR t.topup_status = :topupStatus)
  AND (:from        IS NULL OR t.created_date >= :from)
  AND (:to          IS NULL OR t.created_date <= :to)
ORDER BY t.created_date DESC
```

### Flyway index migration (V9)

```sql
-- Source: V8__analytics_index.sql pattern
CREATE INDEX idx_topup_client_date
    ON main.topup_request(client_id, created_date DESC);
```

`credit_ledger_entry` already has `idx_ledger_client_date` from V3. No ledger index needed.

---

## Data Model Reference

### main.credit_ledger_entry (V3)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| client_id | BIGINT FK | `client_account(id)` |
| entry_type | VARCHAR(30) | `LedgerEntryType` enum values (see below) |
| amount | BIGINT | **Signed**: positive = credit, negative = debit |
| balance_after | BIGINT | Running snapshot — not useful for aggregation |
| reference | VARCHAR(200) | Optional (topup_id, sendRequestId, etc.) |
| created_date | TIMESTAMP | Via `AbstractAuditingEntity` |
| status | VARCHAR(20) | Always ACTIVE for non-deleted rows |

**Index:** `idx_ledger_client_date ON (client_id, created_date DESC)` — already exists.

### LedgerEntryType enum values

| Value | Sign | Meaning |
|-------|------|---------|
| `TOPUP_PENDING` | 0 | Informational entry at topup submission — amount is always 0 |
| `TOPUP_APPROVED` | positive | Admin credits the balance |
| `SMS_RESERVATION` | negative | Credits reserved at send-time |
| `SMS_DEBIT` | negative | Final debit after delivery confirmation |
| `SMS_REFUND` | positive | Refund if delivery fails |

**Net consumption formula:** `sum(SMS_DEBIT) + sum(SMS_REFUND)` (both signed) — the result
is negative (net debits). Or use `ABS(sum(SMS_DEBIT)) - sum(SMS_REFUND)` for a positive
"credits consumed" figure.

### main.topup_request (V4)

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | |
| client_id | BIGINT FK | `client_account(id)` |
| amount | BIGINT | Always positive (CHECK constraint) |
| transaction_id | VARCHAR(200) | Client-provided; unique per client |
| payment_type | VARCHAR(50) | e.g. "MTN_MoMo" |
| account_number | VARCHAR(50) | Optional |
| topup_status | VARCHAR(30) | `TopupStatus`: PENDING_APPROVAL / APPROVED / REJECTED |
| approved_at | TIMESTAMP | Set on approval; null otherwise |
| rejected_at | TIMESTAMP | Set on rejection; null otherwise |
| created_date | TIMESTAMP | Via `AbstractAuditingEntity` |

**Existing index:** `idx_topup_client_status ON (client_id, topup_status)` — covers
status-filtered queries but not date-range. A new index on `(client_id, created_date DESC)`
is recommended.

**TopupService.approve()** writes `topup_id` as `"top_" + id` format (e.g. `top_42`).
Admin-facing topup IDs use this same format — be consistent in the response.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Direct ledger row reads + Java aggregation | Native SQL aggregation in `Repository<Object, Long>` | Phase 8 (current) | Aggregation at DB level; projections; no entity hydration |
| `JpaRepository` for all repos | `Repository<Object, Long>` for pure-aggregation | Phase 8 (prior decision) | Forces correct separation of write-path vs read-path repos |

---

## Open Questions

1. **Sign convention in response**
   - What we know: `SMS_DEBIT` and `SMS_RESERVATION` amounts are stored as negative longs.
     `TOPUP_APPROVED` and `SMS_REFUND` are positive.
   - What's unclear: Should the `sms_debit` field in the response be negative (raw) or
     positive (absolute value representing consumption)?
   - Recommendation: Use positive absolute values in the response for readability; document
     sign convention in the contract record's Javadoc.

2. **Pagination for topup history**
   - What we know: Admin topup history could grow large; `findByClientId` in
     `TopupRequestRepository` returns unbounded `List`.
   - What's unclear: SPEN-03 does not specify pagination; Phase 8 analytics is unbounded
     (daily breakdown only bounded by date range).
   - Recommendation: Return a `List` in the initial implementation. If the topup history
     endpoint is called with a mandatory client_id filter and a bounded date range, the
     result set will be small enough in v1. Document the assumption.

3. **`TOPUP_PENDING` in net-spend breakdown**
   - What we know: `TOPUP_PENDING` entries always have `amount = 0` (informational only).
   - What's unclear: Should `topup_pending` be included in the breakdown response?
     It will always be 0 but confirms the entry type exists.
   - Recommendation: Omit from response or include as 0 — it carries no information.
     Exclude to keep the response clean.

---

## Key File Paths for Planner

### Existing files (read/extend)

| File | What to do |
|------|-----------|
| `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` | Add `ADMIN_SPEND` constant and entry in `SECURED_MAPPINGS` |
| `src/main/java/com/softropic/sendam/gateway/billing/contract/LedgerEntryType.java` | Reference only — do not modify |
| `src/main/java/com/softropic/sendam/gateway/billing/contract/TopupStatus.java` | Reference only — do not modify |

### New files to create

| File | Type |
|------|------|
| `src/main/resources/db/migration/V9__spend_index.sql` | Flyway migration (topup_request date index) |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryRow.java` | Projection interface |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryResponse.java` | Response record |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryRow.java` | Projection interface |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryItem.java` | Single topup DTO record |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryResponse.java` | Response record (wraps list) |
| `src/main/java/com/softropic/sendam/gateway/spend/repo/SpendRepository.java` | `Repository<Object, Long>` |
| `src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java` | `@Transactional(readOnly = true)` |
| `src/main/java/com/softropic/sendam/gateway/spend/api/AdminSpendResource.java` | Admin `@RestController` |

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection of V3, V4 Flyway migrations — table structure is ground truth
- Direct codebase inspection of `LedgerEntryType.java`, `TopupStatus.java` — enum values confirmed
- Direct codebase inspection of `CreditLedgerEntry.java`, `TopupRequestEntity.java` — field types confirmed
- Direct codebase inspection of Phase 8 `DeliveryAnalyticsRepository`, `DeliveryAnalyticsService`, `AdminDeliveryAnalyticsResource` — reference pattern confirmed
- Direct codebase inspection of `AppEndpoints.java` — current SECURED_MAPPINGS entry count confirmed (8 entries)

### Secondary (MEDIUM confidence)
- Spring Data JPA documentation for `Repository<Object, Long>` pattern and projection interfaces — consistent with Phase 8 implementation

---

## Metadata

**Confidence breakdown:**
- Table structure: HIGH — read directly from Flyway migrations and JPA entities
- LedgerEntryType values: HIGH — read directly from enum
- Reference pattern (Phase 8): HIGH — read directly from implemented code
- Security pattern: HIGH — read directly from AppEndpoints and existing admin controllers
- Map.of() entry count: HIGH — counted directly in AppEndpoints.java (8 entries currently)
- SQL aggregation design: HIGH — derived directly from table structure + Phase 8 pattern
- Response shape decisions: MEDIUM — requirements (SPEN-01..03) are terse; open questions documented above

**Research date:** 2026-03-11
**Valid until:** 2026-04-10 (stable codebase; no external dependencies introduced)
