# Phase 16: SMS Monitoring & Webhooks — Research

**Researched:** 2026-03-12
**Domain:** Spring Boot admin REST endpoints (new) + Vue 3 / Quasar admin pages (replacing stubs)
**Confidence:** HIGH (all findings from direct codebase inspection)

---

## Summary

Phase 16 requires backend work before frontend work. There are currently **no admin endpoints** for SMS monitoring (SMSM-01, SMSM-02) or webhook listing (WEBH-01, WEBH-02). The frontend stubs `SmsMonitorPage.vue` and `WebhooksPage.vue` are single-file placeholders with static "Loading..." text. The router, routes, and nav links for these pages already exist from Phase 13.

The backend data model is fully implemented: `SendRequest`, `SendRequestRecipient`, `WebhookEndpoint`, and `WebhookDelivery` entities are all in place. The repositories exist but lack the admin-specific query methods needed. Four new admin REST endpoints must be built; then the frontend pages can consume them.

The phase naturally splits into three tasks:
1. **16-01:** Backend — new admin SMS monitoring + webhook endpoints (Java)
2. **16-02:** Frontend — `SmsMonitorPage.vue` (scheduled SMS list + DLR drill-down)
3. **16-03:** Frontend — `WebhooksPage.vue` (webhook registrations + delivery statuses)

**Primary recommendation:** Build the backend first (16-01), then both frontend pages independently (16-02, 16-03). The frontend follows the exact same patterns as `TopupsPage.vue` and `ClientsPage.vue`.

---

## Standard Stack

All libraries and tools are already present — no new dependencies.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3.5.11 + Spring Data JPA | project-standard | New admin REST controllers + repositories | Existing backend pattern; all admin resources follow this |
| Spring Data `Page<T>` / `Pageable` | project-standard | Paginated admin endpoints | Used in `AdminAuditResource`; `WebhookDelivery` list can be large |
| Vue 3 Composition API (`<script setup>`) | project-standard | Frontend page components | All admin pages use this pattern |
| Quasar Framework | project-standard | `q-page`, `q-markup-table`, `q-tabs`, `q-badge`, `q-inner-loading`, `q-btn` | Used in all existing admin pages |
| vue-i18n | project-standard | `useI18n()` / `t()` | Full i18n required (en-US + fr-FR) |
| `useErrorHandler` | project composable | API error surfacing in pages | FOUND-07 mandate |
| `adminApi` (`src/api/admin/index.js`) | project pattern | Centralized API calls | FOUND-06 mandate |
| `normalizeLongIds` (`src/utils/longToString.js`) | project utility | Safe Long→String coercion | Long ID guard decision; `SendRequest.id`, `WebhookDelivery.id`, `WebhookEndpoint.id`, `clientId` fields are all `Long` |

**Installation:** None.

---

## Backend: What Exists vs What Must Be Built

### Confidence: HIGH — verified from direct file reads

### What Exists (do not rebuild)

| Component | File | Status |
|-----------|------|--------|
| `SendRequest` entity | `gateway/sms/repo/SendRequest.java` | Complete |
| `SendRequestRecipient` entity | `gateway/sms/repo/SendRequestRecipient.java` | Complete |
| `SendRequestRepository` | `gateway/sms/repo/SendRequestRepository.java` | Exists; missing admin query |
| `SendRequestRecipientRepository` | `gateway/sms/repo/SendRequestRecipientRepository.java` | Exists; existing `findBySendRequestIdFk()` usable for DLR list |
| `WebhookEndpoint` entity | `gateway/webhook/repo/WebhookEndpoint.java` | Complete |
| `WebhookDelivery` entity | `gateway/webhook/repo/WebhookDelivery.java` | Complete |
| `WebhookEndpointRepository` | `gateway/webhook/repo/WebhookEndpointRepository.java` | Exists; missing admin list query |
| `WebhookDeliveryRepository` | `gateway/webhook/repo/WebhookDeliveryRepository.java` | Exists; missing admin list query |
| `SmsService.getStatus()` | `gateway/sms/service/SmsService.java` | Client-scoped; admin variant must NOT require `clientId` |
| `WebhookService.getWebhookHealth()` | `gateway/webhook/service/WebhookService.java` | Exists; returns aggregate stats (not per-delivery list) |
| `AdminWebhookHealthResource` | `gateway/webhook/api/AdminWebhookHealthResource.java` | Existing health/stats endpoint only |
| `AdminSmsAnalyticsResource` | `gateway/sms/api/AdminSmsAnalyticsResource.java` | Existing delivery analytics only |

### What Must Be Built (backend)

| Artifact | Action | Notes |
|----------|--------|-------|
| `SendRequestRepository.findScheduledAccepted(Pageable)` | Add query method | ACCEPTED + `scheduleTime IS NOT NULL` + sort by `scheduleTime ASC` |
| `SendRequestRecipientRepository.findBySendRequestIdFk(Long, Pageable)` | Add paginated overload | Existing non-paged method stays; add pageable variant |
| `WebhookEndpointRepository.findAll(Pageable)` or filter by status | Add query | All webhook endpoints for admin visibility |
| `WebhookDeliveryRepository.findAll(Pageable)` or filter by `clientId` | Add query | All delivery records; optionally filter by clientId |
| Admin SMS monitoring service | New or extend existing | Queries above; returns Page DTOs |
| Admin webhook monitoring service | New or extend `WebhookService` | Queries above; returns Page DTOs |
| `AdminSmsMonitorResource` | New REST controller | `/api/admin/sms/scheduled` and `/api/admin/sms/scheduled/{sendRequestId}/dlr` |
| `AdminWebhookMonitorResource` | New REST controller | `/api/admin/webhooks/endpoints` and `/api/admin/webhooks/deliveries` |
| `AppEndpoints.java` constants | Add two new path constants | `ADMIN_SMS_MONITOR` and `ADMIN_WEBHOOKS_MONITOR` for security config |
| Response DTO records | New Java records | `ScheduledSmsRow`, `DlrRow`, `WebhookEndpointRow`, `WebhookDeliveryRow` |

---

## Backend API Contract (to be built)

### Endpoint 1: SMSM-01 — Scheduled SMS list

```
GET /api/admin/sms/scheduled
```

**Query params:** `clientId` (optional Long), `page` (default 0), `size` (default 20)
**Returns:** `Page<ScheduledSmsRow>`
**Filter:** `sendStatus = ACCEPTED AND scheduleTime IS NOT NULL`
**Sort:** `scheduleTime ASC` (soonest first)

**`ScheduledSmsRow` fields:**
| Field | Type | Source |
|-------|------|--------|
| `id` | Long | `SendRequest.id` — needs `longToString()` on frontend |
| `clientId` | Long | `SendRequest.clientId` |
| `sendRequestId` | String | `SendRequest.sendRequestId` |
| `sender` | String | `SendRequest.sender` |
| `messageCount` | int | `SendRequest.messageCount` |
| `scheduleTime` | Instant | `SendRequest.scheduleTime` |
| `reservedCredits` | long | `SendRequest.reservedCredits` |

### Endpoint 2: SMSM-02 — DLR drill-down

```
GET /api/admin/sms/scheduled/{sendRequestId}/dlr
```

**Path variable:** `sendRequestId` (the String identifier, not the database Long id)
**Query params:** `page` (default 0), `size` (default 20)
**Returns:** `Page<DlrRow>` plus envelope fields (`sendRequestId`, `overallStatus`)

**`DlrRow` fields:**
| Field | Type | Source |
|-------|------|--------|
| `id` | Long | `SendRequestRecipient.id` — needs `longToString()` |
| `recipient` | String | `SendRequestRecipient.recipient` |
| `sendStatus` | String | `SendRequestRecipient.sendStatus.name()` |
| `gatewayMessageId` | String | `SendRequestRecipient.gatewayMessageId` |
| `providerMessageId` | String | `SendRequestRecipient.providerMessageId` |
| `segmentsConsumed` | Integer | `SendRequestRecipient.segmentsConsumed` |

**Note:** `SmsService.getStatus()` is client-scoped (enforces `clientId` from auth token). The admin DLR endpoint must be a separate admin-only method without `clientId` binding, or it must accept any `clientId=null` to bypass the filter. A new service method or admin service class is the cleanest approach.

### Endpoint 3: WEBH-01 — Webhook registrations list

```
GET /api/admin/webhooks/endpoints
```

**Query params:** `page` (default 0), `size` (default 20)
**Returns:** `Page<WebhookEndpointRow>`

**`WebhookEndpointRow` fields:**
| Field | Type | Source |
|-------|------|--------|
| `id` | Long | `WebhookEndpoint.id` — needs `longToString()` |
| `clientId` | Long | `WebhookEndpoint.clientId` |
| `publicId` | String | `WebhookEndpoint.publicId` — already a `wh_XXX` string, no prefix needed |
| `url` | String | `WebhookEndpoint.url` |
| `events` | String | `WebhookEndpoint.events` (comma-separated) |
| `status` | String | `WebhookEndpoint.status.name()` |
| `createdDate` | Instant | `WebhookEndpoint.createdDate` |

**Note:** `WebhookEndpointRepository` has `findByClientId()` and `findByClientIdAndStatus()` but no `findAll(Pageable)`. A standard `JpaRepository.findAll(Pageable)` call works without a custom query.

### Endpoint 4: WEBH-02 — Webhook delivery statuses

```
GET /api/admin/webhooks/deliveries
```

**Query params:** `clientId` (optional Long), `attemptStatus` (optional: PENDING/DELIVERED/FAILED/EXHAUSTED), `page` (default 0), `size` (default 20)
**Returns:** `Page<WebhookDeliveryRow>`

**`WebhookDeliveryRow` fields:**
| Field | Type | Source |
|-------|------|--------|
| `id` | Long | `WebhookDelivery.id` — needs `longToString()` |
| `clientId` | Long | `WebhookDelivery.clientId` |
| `sendRequestId` | String | `WebhookDelivery.sendRequestId` |
| `recipient` | String | `WebhookDelivery.recipient` |
| `deliveryStatus` | String | `WebhookDelivery.deliveryStatus` (SMS outcome: "DELIVERED"/"FAILED") |
| `attemptStatus` | String | `WebhookDelivery.attemptStatus.name()` (PENDING/DELIVERED/FAILED/EXHAUSTED) |
| `attemptCount` | int | `WebhookDelivery.attemptCount` |
| `lastAttemptAt` | Instant | `WebhookDelivery.lastAttemptAt` |
| `nextAttemptAt` | Instant | `WebhookDelivery.nextAttemptAt` |
| `httpStatus` | Integer | `WebhookDelivery.httpStatus` |

**Note:** `WebhookDeliveryStatus` has four values — `PENDING`, `DELIVERED`, `FAILED`, `EXHAUSTED`. The WEBH-02 requirement says "PENDING/DELIVERED/FAILED" but `EXHAUSTED` is a real terminal state and should be shown too.

### Security Configuration

New paths need to be registered in `AppEndpoints.java` and the security config:

```java
// Add to AppEndpoints.java:
public static final String ADMIN_SMS_MONITOR   = "/api/admin/sms/**";
public static final String ADMIN_WEBHOOK_MONITOR = "/api/admin/webhooks/**";

// Add to SECURED_MAPPINGS in AppEndpoints static block:
Map.entry(ADMIN_SMS_MONITOR,      new String[]{AuthoritiesConstants.ADMIN}),
Map.entry(ADMIN_WEBHOOK_MONITOR,  new String[]{AuthoritiesConstants.ADMIN}),
```

**Caution:** `ADMIN_ANALYTICS` is already `/api/admin/sms/analytics/**` — the new `ADMIN_SMS_MONITOR = /api/admin/sms/**` would be a superset. Verify ordering/specificity in the security config to avoid unintended narrowing. Safest approach: keep `ADMIN_SMS_MONITOR` as `/api/admin/sms/monitor/**` to be non-overlapping with the existing analytics path.

---

## Frontend Architecture Patterns

### Page Structure

```
SmsMonitorPage.vue      (replaces stub at pages/admin/SmsMonitorPage.vue)
├── Scheduled SMS table (SMSM-01) — paginated list, row click opens DLR panel
└── DLR panel / sub-table (SMSM-02) — shown below or in dialog when row selected
    └── Per-recipient delivery report rows

WebhooksPage.vue        (replaces stub at pages/admin/WebhooksPage.vue)
├── Tab 1: "Registrations" (WEBH-01) — paginated webhook endpoint table
└── Tab 2: "Deliveries" (WEBH-02) — paginated webhook delivery table with status filter
```

### Component Size Rule

Components are capped at 250 lines. Given the dual-feature nature of each page:
- `SmsMonitorPage.vue`: If DLR drill-down is rendered inline (expand-row), the page may approach 250 lines. If it needs a dialog, extract `DlrDialog.vue`.
- `WebhooksPage.vue`: Two-tab layout similar to `TopupsPage.vue` — should fit within 250 lines.

### Existing Pattern References

**TopupsPage.vue** — canonical reference for:
- Two-tab layout (`q-tabs` + `q-tab-panels`)
- `watch(activeTab, loadForTab)` + `onMounted(loadForTab)`
- Separate loading booleans per tab (`isPendingLoading`, `isHistoryLoading`)
- Status badge with `statusColorMap` + `statusLabelMap`
- `clearError()` at start of each load function
- `normalizeLongIds(item, ['id', 'client_id'])` immediately after fetch

**ClientsPage.vue** — canonical reference for:
- `q-inner-loading` overlay pattern
- `hasError` banner at top of page
- Flat + bordered `q-markup-table`

**ServerPagination component** — for paginated tables:
```html
<ServerPagination
  v-if="totalPages > 1"
  :total-elements="totalElements"
  :total-pages="totalPages"
  :page-size="pageSize"
  v-model="currentPage"
  @page-change="onPageChange"
/>
```
Caller passes 0-based index directly to `?page=` query param. The component handles 1-to-0-based conversion in `@page-change`. Wrap in `v-if="totalPages > 1"` (v-if not v-show — truly unmounts).

### Spring Data Page JSON Shape

The backend returns `Page<T>` from Spring Data, which serializes as:

```json
{
  "content": [ {...}, {...} ],
  "totalElements": 47,
  "totalPages": 3,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false
}
```

Frontend accesses `response.content`, `response.totalElements`, `response.totalPages`, `response.size`.

---

## Frontend API Module Additions

All new calls go in `src/api/admin/index.js` as additions to the `adminApi` object.

```javascript
// SMSM-01: Scheduled SMS list
getScheduledSms(params = {}) {
  // params: { clientId, page, size } — all optional
  return api.get('/api/admin/sms/monitor/scheduled', { params })
},

// SMSM-02: DLR per sendRequestId
getDlrForRequest(sendRequestId, params = {}) {
  // params: { page, size }
  return api.get(`/api/admin/sms/monitor/scheduled/${sendRequestId}/dlr`, { params })
},

// WEBH-01: Webhook registrations
getWebhookEndpoints(params = {}) {
  // params: { page, size }
  return api.get('/api/admin/webhooks/endpoints', { params })
},

// WEBH-02: Webhook deliveries
getWebhookDeliveries(params = {}) {
  // params: { clientId, attemptStatus, page, size } — all optional
  return api.get('/api/admin/webhooks/deliveries', { params })
},
```

---

## ID Types Requiring longToString()

**Confidence: HIGH** — all entities inherit `BaseEntity` which uses `@Tsid` on `Long id`.

| Field | Entity | Action |
|-------|--------|--------|
| `id` | `SendRequest` | `normalizeLongIds(row, ['id', 'clientId'])` |
| `clientId` | `SendRequest` | same call |
| `id` | `SendRequestRecipient` | `normalizeLongIds(row, ['id'])` |
| `id` | `WebhookEndpoint` | `normalizeLongIds(row, ['id', 'clientId'])` |
| `clientId` | `WebhookEndpoint` | same call |
| `id` | `WebhookDelivery` | `normalizeLongIds(row, ['id', 'clientId'])` |
| `clientId` | `WebhookDelivery` | same call |

Note: `WebhookEndpoint.publicId` is a `wh_XXX` String — no conversion needed. `SendRequest.sendRequestId` is a String — no conversion needed.

---

## Existing i18n

The following nav keys already exist in both locales:

```javascript
admin.nav.sms      // en: 'SMS Monitor'     fr: 'Surveillance SMS'
admin.nav.webhooks // en: 'Webhooks'        fr: 'Webhooks'
```

**No `admin.sms` or `admin.webhooks` section exists yet** in either `en-US/index.js` or `fr-FR/index.js`. All content keys for both pages must be added in Phase 16.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Paginated query | Custom SQL | Spring Data `JpaRepository.findAll(Pageable)` or JPQL with `Pageable` param | Standard Spring Data — used in AdminAuditResource |
| Error surfacing | Custom error state | `useErrorHandler()` | FOUND-07 mandate |
| API calls | Inline `api.get()` in component | `adminApi.*` methods in `src/api/admin/index.js` | FOUND-06 mandate |
| Loading overlay | Custom CSS spinner | `q-inner-loading` + `q-spinner-dots` | Quasar standard |
| Long ID coercion | Custom serialization | `normalizeLongIds()` from `src/utils/longToString.js` | Established project utility |
| Server-side pagination UI | Custom pagination | `ServerPagination.vue` component | Already built in Phase 13-02 |
| Status badge | Custom styled div | `q-badge :color :label` | Quasar standard |

---

## Common Pitfalls

### Pitfall 1: Overlapping security path `/api/admin/sms/**` conflicts with existing `/api/admin/sms/analytics/**`

**What goes wrong:** Adding `/api/admin/sms/**` to `AppEndpoints.SECURED_MAPPINGS` alongside the existing `ADMIN_ANALYTICS = "/api/admin/sms/analytics/**"` creates path ambiguity in Spring Security's filter chain ordering.

**How to avoid:** Use a distinct path prefix for the new monitor endpoints. Recommend `/api/admin/sms/monitor/**` instead of `/api/admin/sms/**`. This avoids overlap with the existing analytics prefix entirely.

### Pitfall 2: Using `SmsService.getStatus()` for admin DLR drill-down

**What goes wrong:** `SmsService.getStatus()` requires `clientId` from the auth context — it enforces ownership. An admin calling this would need to know the client's numeric ID, which is an awkward API.

**How to avoid:** Build a separate admin service method that fetches the `SendRequest` by `sendRequestId` alone (no `clientId` filter), then paginates its recipients. This can be in a new `AdminSmsMonitorService` or as an additional method in `SmsService` guarded only for admin use.

### Pitfall 3: DLR endpoint uses Long database PK vs String sendRequestId

**What goes wrong:** The URL path variable for DLR drill-down must be the `sendRequestId` String (e.g., `"req_abc123"` or client-supplied UUID), not the database `Long id`. The client-facing API uses the String `sendRequestId` as the canonical identifier.

**How to avoid:** The DLR endpoint path must be `/api/admin/sms/monitor/scheduled/{sendRequestId}/dlr` where `sendRequestId` is the String field, matching how `SmsService.getStatus()` and `SmsResource.getStatus()` already work.

### Pitfall 4: Forgetting Long ID guard on clientId in webhook/SMS rows

**What goes wrong:** `WebhookDelivery.clientId` and `SendRequest.clientId` are `Long` values — they can exceed JS MAX_SAFE_INTEGER for large TSID values. Displaying them raw risks silent truncation.

**How to avoid:** Always call `normalizeLongIds(row, ['id', 'clientId'])` immediately after fetching rows. Apply to every item in the `content` array.

### Pitfall 5: WebhookDelivery.attemptStatus vs deliveryStatus confusion

**What goes wrong:** `WebhookDelivery` has TWO status fields: `deliveryStatus` (the SMS outcome — "DELIVERED"/"FAILED") and `attemptStatus` (the webhook dispatch lifecycle — PENDING/DELIVERED/FAILED/EXHAUSTED). Using the wrong one to filter or display status.

**How to avoid:** The WEBH-02 requirement "monitor outgoing webhook delivery statuses (PENDING/DELIVERED/FAILED)" refers to `attemptStatus`. The `deliveryStatus` is the SMS outcome stored in the payload for the client. Keep them clearly labeled in the UI.

### Pitfall 6: SmsMonitorPage DLR panel exceeds 250-line component limit

**What goes wrong:** A page that has a scheduled SMS table plus an inline expandable DLR sub-table could easily exceed 250 lines.

**How to avoid:** Extract the DLR view into a separate `DlrDialog.vue` component triggered on row click. This keeps `SmsMonitorPage.vue` within 250 lines.

### Pitfall 7: Not clearing drill-down state on pagination change

**What goes wrong:** User selects a row to see DLR, then pages to a new page of scheduled SMS. The DLR panel still shows the previous selection.

**How to avoid:** Reset `selectedSendRequest` to null in the `onPageChange` handler for the outer list.

---

## Code Examples

### Spring Data Page return from repository (pattern from AdminAuditResource)

```java
// Source: gateway/audit/api/AdminAuditResource.java
@GetMapping("/events")
public ResponseEntity<Page<AuditEventRow>> getEvents(
        @PageableDefault(size = 20, sort = "occurred_at", direction = Sort.Direction.DESC)
        Pageable pageable) {
    return ResponseEntity.ok(auditEventService.findEvents(clientId, from, to, pageable));
}
```

### JPQL query for scheduled ACCEPTED requests with pagination

```java
// New method in SendRequestRepository
@Query("SELECT s FROM SendRequest s " +
       "WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
       "AND s.scheduleTime IS NOT NULL " +
       "ORDER BY s.scheduleTime ASC")
Page<SendRequest> findScheduledAccepted(Pageable pageable);
```

### Consuming Spring Page response in frontend

```javascript
async function loadScheduled() {
  isLoading.value = true
  clearError()
  try {
    const data = await adminApi.getScheduledSms({ page: currentPage.value - 1, size: pageSize })
    scheduledRows.value = data.content.map(row => normalizeLongIds(row, ['id', 'clientId']))
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}
```

### WebhookDeliveryStatus badge colors

```javascript
const attemptStatusColorMap = {
  PENDING:   'warning',
  DELIVERED: 'positive',
  FAILED:    'negative',
  EXHAUSTED: 'grey',
}
```

### SendRequestStatus badge colors (for DLR per-recipient)

```javascript
const recipientStatusColorMap = {
  ACCEPTED:      'info',
  SUBMITTED:     'warning',
  COMPLETED:     'positive',
  FAILED:        'negative',
  FINALIZED:     'positive',
  FAIL_FINALIZED: 'negative',
  CANCELLED:     'grey',
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `SmsMonitorPage.vue` stub | Full paginated scheduled SMS list + DLR drill-down | Phase 16 replaces the stub |
| `WebhooksPage.vue` stub | Two-tab: webhook registrations + delivery statuses | Phase 16 replaces the stub |
| No admin SMS monitoring endpoints | `AdminSmsMonitorResource` with 2 endpoints | New backend required |
| No admin webhook listing endpoints | `AdminWebhookMonitorResource` with 2 endpoints | New backend required |

---

## Open Questions

1. **DLR drill-down UI: inline expand row vs dialog**
   - What we know: 250-line component limit; DLR table adds significant HTML
   - What's unclear: Whether expand-row or dialog is preferred UX
   - Recommendation: Use a dialog (`DlrDialog.vue`) triggered on row click — cleaner separation, easier to stay within 250-line limit

2. **Webhook deliveries volume and filter defaults**
   - What we know: One `WebhookDelivery` row is created per recipient per finalized `SendRequest` — volume could be high
   - What's unclear: Whether admin wants to see ALL deliveries or only problem ones (FAILED/EXHAUSTED) by default
   - Recommendation: Default filter to PENDING + FAILED + EXHAUSTED (exclude DELIVERED) with a tab or filter control; this surfaces actionable data first. DELIVERED can be a separate tab or "All" option.

3. **SMSM-01: Only ACCEPTED+scheduled, or include all ACCEPTED?**
   - What we know: Requirement says "ACCEPTED state scheduled for future delivery" — this is `sendStatus=ACCEPTED AND scheduleTime IS NOT NULL`
   - What's unclear: Whether to also include ACCEPTED+immediate (immediate sends that haven't been dispatched yet)
   - Recommendation: Strictly `scheduleTime IS NOT NULL` per the requirement wording. Immediate ACCEPTED sends are dispatched within seconds; they are not "scheduled for future delivery".

4. **Security path prefix for new SMS monitor endpoints**
   - What we know: Existing `ADMIN_ANALYTICS = "/api/admin/sms/analytics/**"` occupies one SMS admin sub-path
   - What's unclear: Whether to use `/api/admin/sms/**` (superset) or `/api/admin/sms/monitor/**` (specific)
   - Recommendation: Use `/api/admin/sms/monitor/**` — avoids any ambiguity with the existing analytics path; a dedicated `ADMIN_SMS_MONITOR` constant in `AppEndpoints.java`.

---

## Sources

### Primary (HIGH confidence)
- Direct file read: `gateway/sms/repo/SendRequest.java` — entity fields, status values
- Direct file read: `gateway/sms/repo/SendRequestRecipient.java` — entity fields
- Direct file read: `gateway/sms/repo/SendRequestRepository.java` — existing queries, confirmed no admin pageable query
- Direct file read: `gateway/sms/repo/SendRequestRecipientRepository.java` — existing non-paged methods
- Direct file read: `gateway/webhook/repo/WebhookEndpoint.java` — entity fields
- Direct file read: `gateway/webhook/repo/WebhookDelivery.java` — entity fields; confirmed two status fields
- Direct file read: `gateway/webhook/repo/WebhookDeliveryRepository.java` — confirmed no admin list query
- Direct file read: `gateway/webhook/repo/WebhookEndpointRepository.java` — confirmed no findAll pageable
- Direct file read: `gateway/webhook/contract/WebhookDeliveryStatus.java` — PENDING/DELIVERED/FAILED/EXHAUSTED
- Direct file read: `gateway/sms/contract/SendRequestStatus.java` — all 7 status values
- Direct file read: `gateway/webhook/api/AdminWebhookHealthResource.java` — confirmed only stats endpoint, no listing
- Direct file read: `gateway/sms/api/AdminSmsAnalyticsResource.java` — confirmed only analytics, no monitor
- Direct file read: `gateway/sms/service/SmsService.java` — confirmed getStatus() is client-scoped
- Direct file read: `gateway/audit/api/AdminAuditResource.java` — canonical pattern for paginated admin endpoints
- Direct file read: `security/config/AppEndpoints.java` — all existing admin paths; confirmed no SMS monitor or webhook listing paths
- Direct file read: `common/persistence/BaseEntity.java` — confirmed `Long id` with `@Tsid`
- Direct file read: `src/frontend/src/pages/admin/SmsMonitorPage.vue` — confirmed stub
- Direct file read: `src/frontend/src/pages/admin/WebhooksPage.vue` — confirmed stub
- Direct file read: `src/frontend/src/router/routes.js` — confirmed routes `/admin/sms` and `/admin/webhooks` already exist
- Direct file read: `src/frontend/src/api/admin/index.js` — confirmed no SMS monitor or webhook monitor methods
- Direct file read: `src/frontend/src/i18n/en-US/index.js` — confirmed no `admin.sms` or `admin.webhooks` section
- Direct file read: `src/frontend/src/i18n/fr-FR/index.js` — confirmed no `admin.sms` or `admin.webhooks` section
- Direct file read: `src/frontend/src/pages/admin/TopupsPage.vue` — canonical frontend pattern to replicate
- Direct file read: `src/frontend/src/components/common/ServerPagination.vue` — confirmed props interface

---

## Metadata

**Confidence breakdown:**
- Backend entity model and fields: HIGH — read directly from Java entities
- Missing admin endpoints: HIGH — exhaustive scan of `/api/` files found no matching controllers
- Client-scope issue in SmsService.getStatus(): HIGH — read service implementation
- Security path conflict risk: HIGH — read AppEndpoints.java in full
- Frontend page stubs: HIGH — read both Vue files; confirmed static placeholder
- Router configuration: HIGH — read routes.js
- i18n gaps: HIGH — read both locale files; no sms/webhooks admin section present
- Spring Page JSON shape: HIGH — well-established Spring Data behavior

**Research date:** 2026-03-12
**Valid until:** Stable — all findings from project source files; valid until entities or patterns change
