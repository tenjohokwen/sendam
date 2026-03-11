# Phase 12: Client Analytics - Research

**Researched:** 2026-03-11
**Domain:** Client-facing analytics endpoints (delivery stats, segment totals, net credit consumption)
**Confidence:** HIGH — all findings are drawn directly from existing source files in the repository.

## Summary

Phase 12 adds three client-facing analytics endpoints. The domain is almost entirely covered by
infrastructure already built in Phases 8 and 9. The existing `DeliveryAnalyticsRepository` and
`SpendRepository` queries already accept a nullable `clientId` parameter — for client endpoints,
that parameter is simply always supplied (from the security context) rather than optional.

The key difference from admin endpoints is the source of `clientId`: admin passes it as an
optional request param; client extracts it as a `Long` cast from
`SecurityContextHolder.getContext().getAuthentication().getPrincipal()`. No filter param
is accepted from the caller. Security is enforced by the `@Order(1)` API-key filter chain
that covers `/v1/**` paths, not by `@PreAuthorize`.

Per the out-of-scope declaration: clients get summary totals only (no `daily_breakdown`), and
credit consumption is a net total only (no per-entry-type breakdown). This means two of the
three client response DTOs are narrower than their admin equivalents.

**Primary recommendation:** Reuse the existing repo and service beans where possible by adding
new service methods scoped to a mandatory clientId; add a dedicated `ClientAnalyticsResource`
controller under `/v1/analytics/**`; register that path in `AppEndpoints` and
`ClientSecurityConfiguration`.

---

## Standard Stack

All stack decisions are already in place. No new dependencies.

### Existing Infrastructure Reused

| File | Role | Reuse Strategy |
|------|------|---------------|
| `gateway/analytics/repo/DeliveryAnalyticsRepository.java` | Native SQL aggregate on `send_request_recipient` | Reuse `findDeliveryStats` method directly — clientId is mandatory for client calls |
| `gateway/analytics/service/DeliveryAnalyticsService.java` | Maps `DeliveryStatRow` to response records | Add new `getClientDeliveryStats` and `getClientSegmentTotals` methods (clientId is `Long`, never null) |
| `gateway/spend/repo/SpendRepository.java` | Native SQL aggregate on `credit_ledger_entry` | Reuse `findSpendSummary` method directly — clientId mandatory |
| `gateway/spend/service/SpendService.java` | Maps `SpendSummaryRow` to response records | Add new `getClientNetCreditsConsumed` method |
| `gateway/analytics/contract/DeliveryStatRow.java` | Projection interface for summary query | Unchanged — already has `getTotalSent`, `getDelivered`, `getFailed`, `getTotalSegments` |
| `gateway/spend/contract/SpendSummaryRow.java` | Projection interface for ledger aggregate | Unchanged — already has `getNetCreditsConsumed` |

### Patterns Already Established

| Pattern | Source | Notes |
|---------|--------|-------|
| Extract `clientId` from security context | `SmsResource`, `CreditResource`, `ClientApiKeyResource` | `(Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal()` |
| No `@PreAuthorize` on client controllers | `SmsResource`, `CreditResource` | API-key filter chain handles auth; `@PreAuthorize` is admin/JWT only |
| `@RequestMapping("/v1/...")` for client paths | All client resources | Client endpoints live under `/v1/`, not `/api/admin/` |
| `@Order(1)` filter chain covers `/v1/**` | `ClientSecurityConfiguration` | New analytics path must fall under a pattern already in the `securityMatcher` list |

---

## Architecture Patterns

### Client Identity Extraction (VERIFIED — HIGH confidence)

`ApiKeyService.authenticate()` returns:

```java
new UsernamePasswordAuthenticationToken(
    keyEntity.getClientId(),   // Long — this is the principal
    null,
    List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
);
```

Every client controller reads it with the same cast:

```java
// Source: SmsResource.java, CreditResource.java, ClientApiKeyResource.java
Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
```

This is the only correct extraction pattern. No `ApiKeyPrincipal` wrapper class exists — the
principal is a raw `Long`.

### Security Chain Coverage for New Path

`ClientSecurityConfiguration.clientApiSecurityFilterChain` uses:

```java
http.securityMatcher(
    AppEndpoints.SMS_API,         // /v1/sms/**
    AppEndpoints.CREDITS_API,     // /v1/credits/**
    AppEndpoints.CLIENT_API_KEYS, // /v1/api/**
    AppEndpoints.WEBHOOKS_API     // /v1/webhooks/**
);
```

The new analytics path `/v1/analytics/**` is NOT currently in this list. It must be added to
both `AppEndpoints` (as a new constant) AND as a new argument to `securityMatcher(...)` in
`ClientSecurityConfiguration`. Without this, the path falls through to the `@Order(2)` JWT
chain and client API key requests will fail with 401.

### SECURED_MAPPINGS — Map.ofEntries (MANDATORY)

`AppEndpoints.SECURED_MAPPINGS` uses `Map.ofEntries(...)` (confirmed in current source — this
was migrated from `Map.of()` in Phase 10). Current entry count: 11 entries. There is no pair
limit with `Map.ofEntries()`. Adding a `CLIENT_ANALYTICS` entry is straightforward.

However: `CLIENT_ANALYTICS` path under `/v1/**` is already covered by the `SECURED` entry
(`/v1/**` → all roles). A separate entry in `SECURED_MAPPINGS` for the client analytics path
is not strictly necessary for the JWT chain (it won't reach the JWT chain). But for consistency
with existing constants (e.g., `SMS_API`, `CREDITS_API` are declared but not in
`SECURED_MAPPINGS`) a constant declaration in `AppEndpoints` is the right pattern. Only
`ClientSecurityConfiguration.securityMatcher(...)` needs updating.

### Recommended Project Structure

New files live in the existing `gateway/analytics` module. No new module needed.

```
gateway/analytics/
├── api/
│   ├── AdminDeliveryAnalyticsResource.java   (existing — unchanged)
│   └── ClientAnalyticsResource.java          (NEW)
├── contract/
│   ├── DeliveryStatRow.java                  (existing — unchanged)
│   ├── DeliveryDailyStatRow.java             (existing — unchanged)
│   ├── DeliveryDailyStat.java                (existing — unchanged)
│   ├── DeliveryStatsResponse.java            (existing — unchanged, admin only)
│   ├── SegmentTotalsResponse.java            (existing — may need client variant or reuse)
│   ├── ClientDeliveryStatsResponse.java      (NEW — narrower, no daily_breakdown)
│   ├── ClientSegmentTotalsResponse.java      (NEW — or reuse SegmentTotalsResponse with null clientId omitted)
│   └── ClientCreditConsumptionResponse.java  (NEW — net total only, no breakdown)
├── repo/
│   └── DeliveryAnalyticsRepository.java      (existing — unchanged)
└── service/
    └── DeliveryAnalyticsService.java         (existing — add client-scoped methods)

gateway/spend/
└── service/
    └── SpendService.java                     (existing — add getClientNetCreditsConsumed)
```

### Anti-Patterns to Avoid

- **Adding `clientId` as a `@RequestParam` on client endpoints:** Client identity is extracted
  from the security context. Accepting it as a request param would allow any client to query
  another client's data.
- **Adding `@PreAuthorize("hasRole('ADMIN')")` to client endpoints:** Client requests carry
  `ROLE_API_CLIENT`, not `ROLE_ADMIN`. The annotation would cause 403 for all client calls.
- **Mounting client endpoints under `/api/`:** That path prefix is used by the JWT chain
  (`@Order(2)`). Client API key auth operates under `/v1/`.
- **Forgetting to add the new path to `ClientSecurityConfiguration.securityMatcher`:** The
  `/v1/analytics/**` path is not currently in the matcher. If omitted, requests reach the JWT
  chain and fail.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Delivery aggregate query | Custom SQL | `DeliveryAnalyticsRepository.findDeliveryStats` | Identical SQL — just call with non-null clientId |
| Segment total query | Custom SQL | `DeliveryAnalyticsRepository.findDeliveryStats` (reuse) | `total_segments` already in the same aggregate row |
| Net credits consumed query | Custom SQL | `SpendRepository.findSpendSummary` | Already computes `net_credits_consumed` correctly |
| Client identity extraction | Custom filter / request param | `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` cast to `Long` | Established pattern across all client resources |

**Key insight:** The SQL queries are already parameterized for mandatory-clientId use. Passing
a non-null `Long clientId` to `findDeliveryStats` and `findSpendSummary` produces exactly the
scoped results Phase 12 requires.

---

## Common Pitfalls

### Pitfall 1: Missing securityMatcher entry

**What goes wrong:** Client requests to `/v1/analytics/**` reach the `@Order(2)` JWT chain.
The JWT chain has no matching path rule, so the request is rejected with 401 "Full authentication
is required".
**Why it happens:** `ClientSecurityConfiguration` explicitly lists paths in `securityMatcher()`.
New paths are not covered automatically.
**How to avoid:** Add `AppEndpoints.CLIENT_ANALYTICS` to the `securityMatcher(...)` call in
`ClientSecurityConfiguration`.
**Warning signs:** 401 response with a valid API key Bearer token.

### Pitfall 2: Reusing admin response records that include `daily_breakdown`

**What goes wrong:** `DeliveryStatsResponse` includes a `daily_breakdown` field. If the client
endpoint returns this record, it exposes admin-style breakdown that is out-of-scope for clients,
and the service would still call `findDailyBreakdown` unnecessarily.
**Why it happens:** Temptation to reuse the existing record directly.
**How to avoid:** Create a separate `ClientDeliveryStatsResponse` record without `daily_breakdown`.
The service method for clients only calls `findDeliveryStats`, not `findDailyBreakdown`.

### Pitfall 3: Reusing SpendSummaryResponse (per-type breakdown exposed to client)

**What goes wrong:** `SpendSummaryResponse` includes `sms_debit`, `sms_refund`,
`topup_approved`, `sms_reservation` fields — the entry-level breakdown. The out-of-scope
declaration says clients get net total only, not entry-level detail.
**Why it happens:** `SpendSummaryRow.getNetCreditsConsumed()` already exists so the temptation
is to map the full row.
**How to avoid:** Create `ClientCreditConsumptionResponse` with only `net_credits_consumed`,
plus the echoed filter fields (`from`, `to`). Map only `row.getNetCreditsConsumed()`.

### Pitfall 4: delivery_rate division-by-zero

**What goes wrong:** `(double) delivered / totalSent * 100.0` throws `ArithmeticException`
when `totalSent == 0` — or returns `NaN`/`Infinity` for double division.
**Why it happens:** New clients with no sends yet will produce a zero aggregate.
**How to avoid:** Guard is already established in `DeliveryAnalyticsService.getDeliveryStats`:
`double rate = totalSent == 0 ? 0.0 : (double) delivered / totalSent * 100.0;`
Copy this guard exactly into the client service method.

### Pitfall 5: TOPUP_PENDING excluded from net computation (already handled)

`SpendRepository.findSpendSummary` excludes `TOPUP_PENDING` via the `CASE` expression (it
only aggregates `SMS_DEBIT`, `SMS_REFUND`, `TOPUP_APPROVED`, `SMS_RESERVATION`). The client
endpoint inherits this correct behavior by reusing the same query — no additional filtering
needed.

---

## Code Examples

### Extracting clientId in a client controller

```java
// Source: SmsResource.java (established pattern)
Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
```

### Calling the delivery stats query with mandatory clientId

```java
// Source: DeliveryAnalyticsRepository.findDeliveryStats — existing method
// For clients, clientId is always non-null; from/to remain optional
DeliveryStatRow summary = repository.findDeliveryStats(clientId, from, to);
```

### Delivery rate guard (established decision)

```java
// Source: DeliveryAnalyticsService.getDeliveryStats
double rate = totalSent == 0 ? 0.0 : (double) delivered / totalSent * 100.0;
```

### Net credits consumed (from SpendSummaryRow — single field)

```java
// Source: SpendRepository.findSpendSummary — existing method
SpendSummaryRow row = spendRepository.findSpendSummary(clientId, from, to);
long netCreditsConsumed = row.getNetCreditsConsumed();
```

### Client controller pattern (no @PreAuthorize, path under /v1/)

```java
// Source: CreditResource.java (established pattern)
@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class ClientAnalyticsResource {

    private final DeliveryAnalyticsService analyticsService;
    private final SpendService spendService;

    @GetMapping("/delivery-stats")
    public ResponseEntity<ClientDeliveryStatsResponse> getDeliveryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(analyticsService.getClientDeliveryStats(clientId, from, to));
    }
    // ... segment-totals, credits endpoints follow same shape
}
```

---

## New Files to Create vs. What to Extend

### New files (create from scratch)

| File | Purpose |
|------|---------|
| `gateway/analytics/contract/ClientDeliveryStatsResponse.java` | Response record: `total_sent`, `delivered`, `failed`, `delivery_rate`, `total_segments` — no `daily_breakdown` |
| `gateway/analytics/contract/ClientSegmentTotalsResponse.java` | Response record: `total_segments`, `from`, `to` — no `client_id` field (client knows their own id) |
| `gateway/analytics/contract/ClientCreditConsumptionResponse.java` | Response record: `net_credits_consumed`, `from`, `to` |
| `gateway/analytics/api/ClientAnalyticsResource.java` | Client REST controller at `/v1/analytics/**` |

### Existing files to extend (add methods)

| File | Change |
|------|--------|
| `gateway/analytics/service/DeliveryAnalyticsService.java` | Add `getClientDeliveryStats(Long clientId, Instant from, Instant to)` and `getClientSegmentTotals(Long clientId, Instant from, Instant to)` |
| `gateway/spend/service/SpendService.java` | Add `getClientNetCreditsConsumed(Long clientId, Instant from, Instant to)` |
| `security/config/AppEndpoints.java` | Add `CLIENT_ANALYTICS = "/v1/analytics/**"` constant |
| `gateway/auth/config/ClientSecurityConfiguration.java` | Add `AppEndpoints.CLIENT_ANALYTICS` to `securityMatcher(...)` call |

### Existing files that are UNCHANGED

| File | Reason |
|------|--------|
| `gateway/analytics/repo/DeliveryAnalyticsRepository.java` | Existing queries are already correct — no new query methods needed |
| `gateway/spend/repo/SpendRepository.java` | Existing `findSpendSummary` already produces `net_credits_consumed` |
| All `contract/*Row.java` projection interfaces | SQL column aliases unchanged |
| `gateway/analytics/api/AdminDeliveryAnalyticsResource.java` | Admin endpoint untouched |
| `gateway/spend/api/AdminSpendResource.java` | Admin endpoint untouched |

---

## AppEndpoints Changes

### New constant

```java
public static final String CLIENT_ANALYTICS = "/v1/analytics/**";
```

### SECURED_MAPPINGS

`CLIENT_ANALYTICS` does NOT need an entry in `SECURED_MAPPINGS`. The path `/v1/analytics/**`
is already covered by the existing `SECURED` entry (`/v1/**` → all authenticated roles). The
`SECURED_MAPPINGS` map is consumed by the `@Order(2)` JWT chain. Client requests never reach
the JWT chain because `ClientSecurityConfiguration` (`@Order(1)`) claims the path.

The `Map.ofEntries(...)` pattern is already in use (confirmed in current source) — no entry
limit concern regardless.

### ClientSecurityConfiguration change

```java
// Existing
http.securityMatcher(
    AppEndpoints.SMS_API,
    AppEndpoints.CREDITS_API,
    AppEndpoints.CLIENT_API_KEYS,
    AppEndpoints.WEBHOOKS_API
);

// After Phase 12
http.securityMatcher(
    AppEndpoints.SMS_API,
    AppEndpoints.CREDITS_API,
    AppEndpoints.CLIENT_API_KEYS,
    AppEndpoints.WEBHOOKS_API,
    AppEndpoints.CLIENT_ANALYTICS   // ADD THIS
);
```

---

## Response DTO Design

### CANL-01 — Client delivery stats (summary only)

```
ClientDeliveryStatsResponse {
    total_sent:      long
    delivered:       long
    failed:          long
    delivery_rate:   double    // 0.0–100.0, guard at total_sent==0
    total_segments:  long
}
```

No `daily_breakdown`. No `client_id` echoed (client knows their own identity).

### CANL-02 — Client billed segment totals

```
ClientSegmentTotalsResponse {
    total_segments:  long
    from:            Instant   // nullable — echoed filter
    to:              Instant   // nullable — echoed filter
}
```

`getSegmentTotals` in the admin service reuses `findDeliveryStats` — same pattern applies for
client. The client service method calls `repository.findDeliveryStats(clientId, from, to)` and
reads only `getTotalSegments()`.

### CANL-03 — Client net credit consumption

```
ClientCreditConsumptionResponse {
    net_credits_consumed:  long
    from:                  Instant   // nullable — echoed filter
    to:                    Instant   // nullable — echoed filter
}
```

Only `getNetCreditsConsumed()` is read from `SpendSummaryRow`. The per-type breakdown fields
(`sms_debit`, `sms_refund`, `topup_approved`, `sms_reservation`) are not included in the
client-facing response per the out-of-scope declaration.

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| `Map.of(...)` in AppEndpoints | `Map.ofEntries(...)` | Migrated in Phase 10 — no 10-pair limit |
| Nullable `clientId` param for admin queries | Same queries with mandatory `clientId` for client queries | No query change needed |

---

## Open Questions

1. **Should `ClientAnalyticsResource` inject both `DeliveryAnalyticsService` and `SpendService`?**
   - What we know: Three endpoints across two service domains. Standard pattern is one controller
     per responsibility, but three tightly-related endpoints may warrant a single controller.
   - What's unclear: Project preference for controller granularity.
   - Recommendation: Single `ClientAnalyticsResource` controller injecting both services is
     simpler and matches how admin phase combined analytics and segment-totals into one resource.

2. **Should `ClientAnalyticsResource` be in `gateway/analytics/api/` or its own package?**
   - What we know: SMS credits consumption is in `gateway/spend`. But the client analytics
     controller aggregates both analytics and spend data under one path.
   - Recommendation: Place in `gateway/analytics/api/` to keep the `/v1/analytics/**` path
     ownership clear. The controller delegates to `SpendService` across module boundaries,
     which is acceptable at the API layer.

---

## Sources

### Primary (HIGH confidence)

All findings verified directly from source files in the repository:

- `src/main/java/com/softropic/sendam/gateway/analytics/repo/DeliveryAnalyticsRepository.java` — query signatures
- `src/main/java/com/softropic/sendam/gateway/analytics/service/DeliveryAnalyticsService.java` — service pattern
- `src/main/java/com/softropic/sendam/gateway/analytics/api/AdminDeliveryAnalyticsResource.java` — admin controller pattern
- `src/main/java/com/softropic/sendam/gateway/spend/repo/SpendRepository.java` — spend query signatures
- `src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java` — spend service pattern
- `src/main/java/com/softropic/sendam/gateway/spend/api/AdminSpendResource.java` — spend admin controller
- `src/main/java/com/softropic/sendam/gateway/auth/service/ApiKeyService.java` — principal type is `Long`
- `src/main/java/com/softropic/sendam/gateway/auth/config/ClientSecurityConfiguration.java` — securityMatcher list
- `src/main/java/com/softropic/sendam/gateway/auth/config/ApiKeyAuthenticationFilter.java` — filter pattern
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` — current constants and SECURED_MAPPINGS
- `src/main/java/com/softropic/sendam/gateway/sms/api/SmsResource.java` — clientId extraction pattern
- `src/main/java/com/softropic/sendam/gateway/billing/api/CreditResource.java` — clientId extraction pattern
- `src/main/java/com/softropic/sendam/gateway/auth/api/ClientApiKeyResource.java` — clientId extraction pattern
- All contract files: `DeliveryStatRow`, `DeliveryStatsResponse`, `SegmentTotalsResponse`, `SpendSummaryRow`, `SpendSummaryResponse`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries and patterns are in production use in this repo
- Architecture: HIGH — client identity extraction, security chain, and path registration all verified from source
- Pitfalls: HIGH — all pitfalls derived from reading actual code and confirmed prior decisions

**Research date:** 2026-03-11
**Valid until:** Stable — only changes if `ApiKeyService.authenticate()` changes principal type,
or if `ClientSecurityConfiguration` is restructured.
