# Phase 24: Deviation Alert Management - Research

**Researched:** 2026-03-17
**Domain:** Admin REST API for deviation alert lifecycle management
**Confidence:** HIGH — all findings derived from direct codebase inspection

---

## Summary

Phase 24 exposes a unified admin API to list, acknowledge, and resolve deviation alerts across two
physically separate tables: `main.segment_deviation_alert` (SEGMENT and PLATFORM_FREEZE alert types,
Phase 22) and `main.balance_deviation_alert` (BALANCE alert type, Phase 23). Both tables currently
store only creation data with an `EntityStatus.ACTIVE` column that does not model the
OPEN/ACKNOWLEDGED/RESOLVED lifecycle required here.

The central design problem is that no alert-level status lifecycle, no acknowledgement state, and no
audit trail exist yet. Phase 24 must add them. The two key decisions that shape every downstream
choice are: (1) how to add the new status field (alert-status enum separate from EntityStatus), and
(2) how to store the immutable audit trail of transitions and admin notes (new child table versus
JSONB column on each alert table).

The recommendation is: introduce a new `AlertStatus` enum (OPEN/ACKNOWLEDGED/RESOLVED) as a
distinct `alert_status` column on both alert tables; store the transition audit trail in a new shared
`main.deviation_alert_event` child table (separate rows per transition, FK back to each alert table
via two nullable FK columns); expose separate query endpoints per table type but return a polymorphic
`DeviationAlertDto` union using a `type` discriminator field; use JPQL `@Query` with optional
parameter filtering and `Page<Pageable>` — no JPA Specification needed.

**Primary recommendation:** Two-table separate-endpoint approach with a shared `deviation_alert_event`
child table for the audit trail. Do NOT store audit entries as JSONB on the alert rows.

---

## Standard Stack

The project is already fully established. No new libraries are required.

### Core (already in use)
| Library | Purpose | Location |
|---------|---------|---------|
| Spring Data JPA | `@Query` + `Page<T>` pagination | all `.repo` packages |
| hypersistence-utils | `@Tsid` PK generation | `BaseEntity` |
| hypersistence-utils | `JsonType` JSONB mapping | `SegmentDeviationAlert` |
| Flyway | DB migrations | `src/main/resources/db/migration/` |
| Lombok `@SuperBuilder` | Entity construction | all alert entities |
| `@PreAuthorize("hasRole('ADMIN')")` | Class-level security | all admin resources |
| `ApplicationException` hierarchy | Domain exceptions | `common/exception/` |
| `ResourceNotFoundException` | 404 for missing alert | `common/exception/` |

### No New Dependencies
Everything needed exists. Do not introduce Querydsl, JPA Specification, or MapStruct.

**Next migration version:** `V16__deviation_alert_lifecycle.sql`

---

## Architecture Patterns

### Existing Admin Resource Pattern
All admin REST resources follow this exact structure (confirmed from AdminPlatformCreditResource,
AdminClientFreezeResource, AdminAuditResource, AdminSmsMonitorResource):

```java
@RestController
@RequestMapping("/api/admin/...")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminXxxResource {
    // class-level @PreAuthorize covers all methods
}
```

The `AppEndpoints` class must receive a new constant for the new path, and the `SECURED_MAPPINGS`
static block must receive a corresponding entry. Both are in
`security/config/AppEndpoints.java`. The `SecurityConfiguration` reads `SECURED_ENDPOINTS` (which is
`List.copyOf(SECURED_MAPPINGS.keySet())`) and `SECURED_MAPPINGS` via `SecuredHttpEndpointGuard`, so
adding to `SECURED_MAPPINGS` is sufficient — no change to `SecurityConfiguration` itself.

### Pagination Pattern
Two existing patterns exist. Use the `@PageableDefault` + `Page<T>` approach from
`AdminAuditResource` (not the manual `PageRequest.of(page, size)` approach from
`PlatformCreditService`):

```java
// Source: AdminAuditResource, AdminSmsMonitorResource
@GetMapping("/alerts")
public ResponseEntity<Page<DeviationAlertDto>> listAlerts(
        @RequestParam(required = false) DeviationAlertType type,
        @RequestParam(required = false) AlertStatus alertStatus,
        @PageableDefault(size = 20, sort = "created_date", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(deviationAlertService.listAlerts(type, alertStatus, pageable));
}
```

### Repository Query Pattern
Use JPQL `@Query` with nullable optional parameters, matching the
`PlatformCreditLedgerRepository` and `AuditEventRepository` patterns:

```java
// Source: PlatformCreditLedgerRepository
@Query("SELECT e FROM Foo e WHERE (:type IS NULL OR e.type = :type) ORDER BY e.createdDate DESC")
Page<Foo> findByOptionalType(@Param("type") FooType type, Pageable pageable);
```

For multi-condition optional filtering across both nullable enum params:

```java
// Segment alerts (JPQL)
@Query("""
    SELECT a FROM SegmentDeviationAlert a
    WHERE (:type IS NULL OR a.alertType = :type)
      AND (:alertStatus IS NULL OR a.alertStatus = :alertStatus)
    ORDER BY a.createdDate DESC
    """)
Page<SegmentDeviationAlert> findByOptionalFilters(
    @Param("type") DeviationAlertType type,
    @Param("alertStatus") AlertStatus alertStatus,
    Pageable pageable);
```

**Note:** JPQL enum comparison with nullable params works reliably when the param type matches the
entity field type. No `CAST` workaround needed for enum params (only needed for native SQL with
PostgreSQL type inference issues as seen in `TopupRequestRepository` and `AuditEventRepository`).

### Recommended Project Structure

Phase 24 adds the following files, all within the existing `gateway/billing/` module:

```
gateway/billing/
  contract/
    AlertStatus.java                      # new enum: OPEN / ACKNOWLEDGED / RESOLVED
    AlertStatusTransitionException.java   # new: thrown on invalid state transition
    DeviationAlertDto.java                # new: union response DTO (record)
    DeviationAlertEventDto.java           # new: audit trail entry DTO (record)
    DeviationAlertNoteRequest.java        # new: acknowledge/resolve request body (record)
  repo/
    AlertStatus (on SegmentDeviationAlert)   # new column: alert_status
    AlertStatus (on BalanceDeviationAlert)   # new column: alert_status
    DeviationAlertEvent.java                 # new JPA entity for audit trail
    DeviationAlertEventRepository.java       # new Spring Data repository
    SegmentDeviationAlertRepository          # extends to add findByOptionalFilters
    BalanceDeviationAlertRepository          # extends to add findByOptionalFilters
  service/
    DeviationAlertManagementService.java     # new: list/acknowledge/resolve logic
  api/
    AdminDeviationAlertResource.java         # new: REST controller
  config/  (already exists via BillingConfig)
    (no changes needed)
resources/db/migration/
  V16__deviation_alert_lifecycle.sql         # new: ALTER + new table
security/config/
  AppEndpoints.java                          # add ADMIN_DEVIATION_ALERTS constant
```

---

## Key Design Decisions

### Decision 1: AlertStatus Enum (NOT EntityStatus)

`EntityStatus` (ACTIVE/INACTIVE/DELETED) is the entity lifecycle status, not the business alert
status. It must not be repurposed. Both alert entities already use `EntityStatus.ACTIVE` for the
active/valid row state — this should remain unchanged.

Introduce a new enum `AlertStatus` in `gateway/billing/contract/`:

```java
// Source: direct analysis of requirements DEVMGMT-03, DEVMGMT-04
package com.softropic.sendam.gateway.billing.contract;

public enum AlertStatus {
    OPEN,           // initial state when alert is first created
    ACKNOWLEDGED,   // admin has noted the alert (DEVMGMT-03)
    RESOLVED        // admin has resolved the alert (DEVMGMT-04)
}
```

Both `SegmentDeviationAlert` and `BalanceDeviationAlert` need:
- New field: `@Enumerated(EnumType.STRING) @Column(name = "alert_status") AlertStatus alertStatus`
- `@Builder.Default alertStatus = AlertStatus.OPEN`

The migration V16 must `ALTER TABLE` both existing tables to add `alert_status` column.

### Decision 2: Audit Trail — New Child Table (not JSONB)

DEVMGMT-05 requires an "immutable audit trail of all status transitions with timestamps and all admin
notes." Options:

| Approach | Pro | Con |
|----------|-----|-----|
| JSONB list column on alert row | Simple | Not truly immutable; requires JSON mutation; hard to query; PostgreSQL JSONB append requires full row rewrite |
| Separate `deviation_alert_event` table | Immutable append-only; queryable; consistent with audit_event pattern | One extra table |
| Hibernate Envers (`@Audited`) | Already on AbstractAuditingEntity | Envers revision tables contain ALL columns; not selective; produces complex joins; note field not part of entity columns |

**Use a dedicated `main.deviation_alert_event` table.** This mirrors the existing
`main.audit_event` table pattern and is append-only by design.

The `deviation_alert_event` table schema:

```sql
CREATE TABLE main.deviation_alert_event (
    id                    BIGINT       PRIMARY KEY,          -- TSID
    segment_alert_id_fk   BIGINT       REFERENCES main.segment_deviation_alert(id),
    balance_alert_id_fk   BIGINT       REFERENCES main.balance_deviation_alert(id),
    alert_type            VARCHAR(30)  NOT NULL,             -- SEGMENT / PLATFORM_FREEZE / BALANCE
    previous_status       VARCHAR(20)  NOT NULL,             -- OPEN / ACKNOWLEDGED / RESOLVED
    new_status            VARCHAR(20)  NOT NULL,
    admin_note            TEXT         NOT NULL,
    acted_by              VARCHAR(50)  NOT NULL,
    acted_at              TIMESTAMPTZ  NOT NULL
);
-- Exactly one of segment_alert_id_fk or balance_alert_id_fk is non-null per row
```

The JPA entity `DeviationAlertEvent` extends `BaseEntity` (not `AbstractAuditingEntity` — it should
not be `@Audited` to avoid envers recursion; and it has its own `acted_at`/`acted_by` fields
instead of the inherited createdDate/createdBy which would require the Spring Security context to be
present at save time).

### Decision 3: Unified Listing — Single Endpoint, Two Queries, Union DTO

Three alert types exist: SEGMENT, PLATFORM_FREEZE (both on `segment_deviation_alert`), and BALANCE
(on `balance_deviation_alert`). The client filters by type and/or status.

Options:
- **Two endpoints** (`/api/admin/deviations/segment-alerts`, `/api/admin/deviations/balance-alerts`): Clean separation but client must call two endpoints to see all alerts.
- **Single endpoint** with union in Java: Query both tables based on requested type filter, merge and sort in service layer. Simple but loses server-side pagination accuracy.
- **Single endpoint** with UNION SQL native query: Requires a native query joining two unlike tables; complex.

**Recommendation: Single endpoint with service-layer routing.**

When `type` filter is BALANCE → query only `balance_deviation_alert`.
When `type` filter is SEGMENT or PLATFORM_FREEZE → query only `segment_deviation_alert`.
When `type` is null (all) → query both tables, merge results in Java, sort by `createdDate DESC`,
apply manual page slice.

The service returns `Page<DeviationAlertDto>` where `DeviationAlertDto` is a Java record with a
discriminated union shape:

```java
// Source: requirements DEVMGMT-01, DEVMGMT-02
public record DeviationAlertDto(
    Long id,
    DeviationAlertType type,
    AlertStatus alertStatus,
    long delta,
    Instant createdDate,
    // SEGMENT / PLATFORM_FREEZE fields (null for BALANCE)
    String sendRequestRef,
    Long clientId,
    Long shortfallAmount,
    Long unrecoveredAmount,
    String financialAction,
    boolean clientFrozen,
    boolean platformFrozen,
    List<RecipientBreakdownDto> perRecipientBreakdown,  // null for non-SEGMENT
    // BALANCE fields (null for SEGMENT / PLATFORM_FREEZE)
    Long nexahBalance,
    Long sendamBalance,
    // audit trail
    List<DeviationAlertEventDto> auditTrail
) {}
```

When `type` is null (list all), the `auditTrail` list can be omitted from list results for
performance, included only on single-alert fetch. This avoids N+1 fetches on the list endpoint.

For the detail fetch (`GET /api/admin/deviations/alerts/{id}?type=SEGMENT`), the type parameter is
required to route to the correct table.

**Alternative considered:** Polymorphic JPA `@Inheritance` on a base `DeviationAlert` entity mapped
to both tables. Rejected — the two tables have incompatible structures (one has JSONB + FK to
send_request; one does not). JPA table-per-class inheritance would work mechanically but is
over-engineered for two tables with divergent schemas.

### Decision 4: Status Transition Validation

State machine: `OPEN → ACKNOWLEDGED → RESOLVED` (linear, no reversal).

```
OPEN        → ACKNOWLEDGED  (acknowledge action, mandatory note)
ACKNOWLEDGED → RESOLVED     (resolve action, mandatory note)
OPEN         → RESOLVED     (also valid — direct resolution without prior acknowledgement)
ACKNOWLEDGED → OPEN         INVALID
RESOLVED    → any           INVALID
```

Implement as a guard method in the service or on the `AlertStatus` enum:

```java
// Source: analysis of DEVMGMT-03, DEVMGMT-04
public enum AlertStatus {
    OPEN, ACKNOWLEDGED, RESOLVED;

    public boolean canTransitionTo(AlertStatus target) {
        return switch (this) {
            case OPEN         -> target == ACKNOWLEDGED || target == RESOLVED;
            case ACKNOWLEDGED -> target == RESOLVED;
            case RESOLVED     -> false;
        };
    }
}
```

Throw `AlertStatusTransitionException extends ApplicationException` on invalid transitions. This
follows the existing `TopupAlreadyProcessedException` pattern.

---

## Existing Entity Details (Confirmed from Source)

### SegmentDeviationAlert (gateway/billing/repo/)
- Table: `main.segment_deviation_alert`
- PK: `Long id` — `@Tsid` (BaseEntity)
- Inherited from AbstractAuditingEntity: `createdBy`, `createdDate`, `lastModifiedBy`,
  `lastModifiedDate`, `requestId`, `sessionId`, `status` (EntityStatus)
- Own fields: `sendRequestIdFk` (Long, FK), `sendRequestRef` (String 200), `clientId` (Long),
  `alertType` (DeviationAlertType enum), `expectedTotal` (long), `actualTotal` (long),
  `delta` (long), `shortfallAmount` (Long nullable), `clientFrozen` (boolean),
  `platformFrozen` (boolean), `unrecoveredAmount` (Long nullable),
  `perRecipientBreakdown` (List<RecipientDeviationEntry>, JSONB),
  `financialAction` (String 30)
- Alert types stored here: SEGMENT, PLATFORM_FREEZE
- Inner record: `RecipientDeviationEntry(String recipient, int expectedSegments, int actualSegments, int delta)`
- **No alert_status column yet** — V16 must add it

### BalanceDeviationAlert (gateway/billing/repo/)
- Table: `main.balance_deviation_alert`
- PK: `Long id` — `@Tsid` (BaseEntity)
- Inherited: same as SegmentDeviationAlert
- Own fields: `alertType` (DeviationAlertType.BALANCE only), `nexahBalance` (long),
  `sendamBalance` (long), `delta` (long)
- **No alert_status column yet** — V16 must add it

### Both Repositories (currently stubs)
- `SegmentDeviationAlertRepository extends JpaRepository<SegmentDeviationAlert, Long>`
- `BalanceDeviationAlertRepository extends JpaRepository<BalanceDeviationAlert, Long>`
- Comment in both: "Phase 24 will add query methods"

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pagination | Manual list slice | Spring Data `Page<T>` + `Pageable` | Consistent with all other admin endpoints |
| Filter queries | Dynamic query builders | JPQL `@Query` with nullable params | Established pattern (AuditEventRepository, PlatformCreditLedgerRepository) |
| ID generation | `BIGSERIAL` sequences | `@Tsid` on `BaseEntity` | V15 already uses BIGINT PK (not BIGSERIAL); TSID generates at app layer |
| Audit trail as text | String concatenation fields | `deviation_alert_event` child table | Queryable, immutable, append-only |
| Exception codes | Raw strings | `ApplicationException` with `ErrorCode` enum | Consistent with existing domain exceptions |
| Status guard | String comparisons | `AlertStatus.canTransitionTo()` method | Centralized, testable, type-safe |
| Security | Custom filter logic | `@PreAuthorize("hasRole('ADMIN')")` class-level + AppEndpoints entry | Belt-and-suspenders pattern used by all admin resources |

---

## Common Pitfalls

### Pitfall 1: Reusing EntityStatus for Alert Lifecycle
**What goes wrong:** Adding ACKNOWLEDGED/RESOLVED to `EntityStatus` or conflating the entity
lifecycle status (ACTIVE/INACTIVE/DELETED) with the business status (OPEN/ACKNOWLEDGED/RESOLVED).
**Why it happens:** EntityStatus is already present on the entity and maps to a column.
**How to avoid:** Add a separate `alert_status` column mapped to a new `AlertStatus` enum. Leave
`EntityStatus.ACTIVE` on the alert rows untouched throughout their lifecycle.

### Pitfall 2: Missing `alert_status` Index on Both Tables
**What goes wrong:** The list endpoint filter on `alertStatus` does a full table scan.
**Why it happens:** V16 adds the column but forgets the index.
**How to avoid:** V16 must include `CREATE INDEX` on `alert_status` for both tables. V15 already
demonstrates this pattern (`idx_balance_deviation_alert_status`).

### Pitfall 3: N+1 Query on Audit Trail Fetch
**What goes wrong:** Loading `DeviationAlertEvent` entries inside a loop over paged alert results.
**How to avoid:** Do NOT join audit trail entries into the list endpoint response. Return
`auditTrail` only on the single-alert detail fetch endpoint. Planner should specify two separate
endpoints: list (no trail) and detail (with trail).

### Pitfall 4: JPQL Optional Enum Filter Binding
**What goes wrong:** `(:type IS NULL OR a.alertType = :type)` fails at runtime when `:type` is null
and the enum type doesn't match PostgreSQL's type inference in native queries.
**How to avoid:** Use JPQL (not nativeQuery=true) for enum filtering. JPQL handles null enum
parameters cleanly without CAST workarounds. Reserve native SQL for text/timestamp filters where
PostgreSQL type inference issues have been observed (see `TopupRequestRepository`,
`AuditEventRepository`).

### Pitfall 5: Alert-Type Routing on Acknowledge/Resolve
**What goes wrong:** Acknowledge/resolve endpoints that accept only an `id` cannot determine which
table to look in without also receiving the alert type.
**How to avoid:** Endpoint paths should include the type: `PUT /api/admin/deviations/{type}/{id}/acknowledge`
where `{type}` is the `DeviationAlertType` enum value (SEGMENT, PLATFORM_FREEZE, BALANCE). This
eliminates ambiguity without requiring an extra DB lookup in a wrong table.

### Pitfall 6: AppEndpoints Not Updated
**What goes wrong:** The new `/api/admin/deviations/**` path is not added to `SECURED_MAPPINGS`,
so `SecuredHttpEndpointGuard` does not enforce authentication at the filter level.
**How to avoid:** Any new admin path constant must be added to both the constant declaration and
the `SECURED_MAPPINGS` static block in `AppEndpoints.java`. The `@PreAuthorize` class annotation
is the second layer, not the only layer.

### Pitfall 7: RESOLVED Alert Cannot Be Re-Opened
**What goes wrong:** Service code omits the transition guard and allows any status update.
**How to avoid:** Before every status write, call `currentStatus.canTransitionTo(targetStatus)` and
throw `AlertStatusTransitionException` if false. Test covers OPEN→ACKNOWLEDGED, OPEN→RESOLVED,
ACKNOWLEDGED→RESOLVED, RESOLVED→anything (all invalid).

---

## Code Examples

### JPQL Filtering with Optional Enum Parameters (HIGH confidence)
```java
// Source: PlatformCreditLedgerRepository (codebase)
@Query("SELECT a FROM SegmentDeviationAlert a " +
       "WHERE (:type IS NULL OR a.alertType = :type) " +
       "  AND (:alertStatus IS NULL OR a.alertStatus = :alertStatus) " +
       "ORDER BY a.createdDate DESC")
Page<SegmentDeviationAlert> findByOptionalFilters(
    @Param("type") DeviationAlertType type,
    @Param("alertStatus") AlertStatus alertStatus,
    Pageable pageable);
```

### Acknowledge Endpoint Pattern (HIGH confidence)
```java
// Source: AdminClientFreezeResource pattern (codebase)
@PutMapping("/{id}/acknowledge")
public ResponseEntity<DeviationAlertDto> acknowledge(
        @PathVariable Long id,
        @Valid @RequestBody DeviationAlertNoteRequest request) {
    return ResponseEntity.ok(deviationAlertManagementService.acknowledge(id, request.note()));
}
```

### Status Transition Guard (HIGH confidence)
```java
// Source: derived from AlertStatus state machine analysis
if (!currentAlert.getAlertStatus().canTransitionTo(AlertStatus.ACKNOWLEDGED)) {
    throw new AlertStatusTransitionException(
        "Cannot acknowledge alert in status " + currentAlert.getAlertStatus(),
        currentAlert.getId(),
        currentAlert.getAlertStatus());
}
```

### DeviationAlertEvent Entity (HIGH confidence)
```java
// Extends BaseEntity (not AbstractAuditingEntity) — owns its own acted_at/acted_by
@Entity
@Table(name = "deviation_alert_event", schema = "main")
@SuperBuilder @NoArgsConstructor @AllArgsConstructor @Getter @Setter
public class DeviationAlertEvent extends BaseEntity {

    @Column(name = "segment_alert_id_fk")
    private Long segmentAlertIdFk;       // null for BALANCE alerts

    @Column(name = "balance_alert_id_fk")
    private Long balanceAlertIdFk;       // null for SEGMENT/PLATFORM_FREEZE alerts

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private DeviationAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private AlertStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AlertStatus newStatus;

    @Column(name = "admin_note", nullable = false, columnDefinition = "text")
    private String adminNote;

    @Column(name = "acted_by", nullable = false, length = 50)
    private String actedBy;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;
}
```

### Request Body for Acknowledge/Resolve (HIGH confidence)
```java
// Source: ClientFreezeRequest/PlatformFreezeRequest patterns (codebase)
public record DeviationAlertNoteRequest(
    @NotBlank @Size(max = 500) String note
) {}
```

---

## API Endpoint Design

Recommended URL structure (to add to AppEndpoints):

```
ADMIN_DEVIATION_ALERTS = "/api/admin/deviations/**"
```

Endpoints:
```
GET    /api/admin/deviations/alerts
       ?type=SEGMENT|PLATFORM_FREEZE|BALANCE  (optional)
       ?alertStatus=OPEN|ACKNOWLEDGED|RESOLVED (optional)
       &page=0&size=20&sort=createdDate,desc
       → Page<DeviationAlertDto> (no auditTrail in list items)

GET    /api/admin/deviations/{type}/{id}
       → DeviationAlertDto (with full auditTrail list)

PUT    /api/admin/deviations/{type}/{id}/acknowledge
       body: { "note": "..." }
       → DeviationAlertDto (updated)

PUT    /api/admin/deviations/{type}/{id}/resolve
       body: { "note": "..." }
       → DeviationAlertDto (updated)
```

Where `{type}` is the string value of `DeviationAlertType` (SEGMENT, PLATFORM_FREEZE, BALANCE).
This allows the controller to route to the correct table without a blind search.

---

## State of the Art

| Current State | Phase 24 State | Notes |
|---------------|----------------|-------|
| Both repos are save()-only stubs | Repos get JPQL query methods | Filter by type + alertStatus |
| No alert_status column | `alert_status VARCHAR(20)` on both tables | V16 ALTER TABLE |
| No audit trail | `main.deviation_alert_event` table | Append-only, immutable |
| No admin API for alerts | `AdminDeviationAlertResource` at `/api/admin/deviations/**` | class-level @PreAuthorize |
| `DeviationAlertType` has 3 values | Unchanged | SEGMENT, PLATFORM_FREEZE, BALANCE |
| `AuditEventType` has SEGMENT_DEVIATION, PLATFORM_FREEZE_SHORTFALL, BALANCE_DEVIATION | Add DEVIATION_ALERT_ACKNOWLEDGED, DEVIATION_ALERT_RESOLVED | Required for audit log |

---

## Open Questions

1. **Direct OPEN→RESOLVED transition**
   - What we know: Requirements mention DEVMGMT-03 (acknowledge) and DEVMGMT-04 (resolve) as separate
     actions, each mandatory.
   - What's unclear: Whether an alert can skip ACKNOWLEDGED and go directly to RESOLVED.
   - Recommendation: Allow OPEN→RESOLVED for operational flexibility (admins can resolve trivial
     alerts in one step). Make the transition guard permissive in that direction.

2. **Audit trail fetch strategy for list endpoint**
   - What we know: Including auditTrail on paginated list results causes N+1 queries.
   - What's unclear: Whether the API consumer needs trail entries on list items.
   - Recommendation: List endpoint returns `auditTrail: null` (or omit field). Detail endpoint
     includes full trail. Planner should specify this explicitly in the plan.

3. **`actedBy` source for DevitionAlertEvent**
   - What we know: Admin actions go through JWT auth; `SecurityContextHolder` provides the current
     username at service layer.
   - What's unclear: Exact utility class to extract the principal username.
   - Recommendation: Use `SecurityUtil` (exists in `security/service/SecurityUtil.java` — confirmed
     in SecurityConfiguration import) or `SecurityContextHolder.getContext().getAuthentication().getName()`.
     Planner should reference the existing `SecurityUtil` bean.

---

## Sources

### Primary (HIGH confidence)
- Direct read: `SegmentDeviationAlert.java`, `BalanceDeviationAlert.java` — field names, types, JSONB mapping
- Direct read: `SegmentDeviationAlertRepository.java`, `BalanceDeviationAlertRepository.java` — stub state
- Direct read: `V14__segment_deviation_alert.sql`, `V15__balance_deviation_alert.sql` — DDL
- Direct read: `AbstractAuditingEntity.java`, `BaseEntity.java` — inheritance structure
- Direct read: `EntityStatus.java`, `DeviationAlertType.java` — existing enums
- Direct read: `AppEndpoints.java`, `SecurityConfiguration.java` — security wiring pattern
- Direct read: `AdminPlatformCreditResource.java`, `AdminClientFreezeResource.java`, `AdminAuditResource.java`, `AdminSmsMonitorResource.java` — admin resource patterns
- Direct read: `PlatformCreditLedgerRepository.java`, `AuditEventRepository.java` — pagination + @Query patterns
- Direct read: `TopupRequestRepository.java` — native query + optional filter patterns
- Direct read: `ApplicationException.java`, `ResourceNotFoundException.java`, `TopupAlreadyProcessedException.java` — exception hierarchy

### Secondary (MEDIUM confidence)
- Analysis of SECURED_MAPPINGS structure in AppEndpoints — new constant requires both a field declaration and a SECURED_MAPPINGS entry

---

## Metadata

**Confidence breakdown:**
- Existing entity structure: HIGH — read directly from source
- Audit trail approach: HIGH — elimination analysis of all options against existing patterns
- Repository query patterns: HIGH — copied from working implementations in same codebase
- Status transition design: HIGH — derived from requirements + existing exception patterns
- API URL design: HIGH — follows all existing admin resource conventions
- actedBy extraction: MEDIUM — SecurityUtil exists but exact method signature not inspected

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable domain, no external libraries)
