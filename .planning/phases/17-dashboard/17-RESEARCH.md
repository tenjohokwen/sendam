# Phase 17: Dashboard - Research

**Researched:** 2026-03-12
**Domain:** Vue 3 + Quasar admin dashboard, Spring Boot aggregation endpoints
**Confidence:** HIGH

## Summary

Phase 17 replaces the 12-line `AdminDashboardPage.vue` stub with a real overview page. The
backend already exposes every piece of data the requirements call for across six existing
endpoints. No new backend aggregation controller is needed — the dashboard calls existing
endpoints in parallel and assembles the results client-side.

The circuit breaker state lives at `GET /api/admin/health/nexah/circuit-breaker` and returns
a typed record that maps cleanly to a colour-coded status chip. Provider send totals are a
separate call at `GET /api/admin/health/nexah/provider-stats`. All other required numbers come
from `/api/admin/sms/analytics/delivery-stats`, `/api/admin/credits/summary`,
`/api/admin/health/webhooks/stats`, and `/api/admin/clients`.

The 250-line cap forces three child components:
`DashboardSystemCard.vue` (circuit breaker + provider stats),
`DashboardSmsCard.vue` (delivery stats, daily breakdown),
`DashboardBillingCard.vue` (credit spend + active clients).
The page itself becomes a thin orchestrator that fires all loads in parallel.

**Primary recommendation:** Call all six endpoints in parallel via `Promise.all` in
`AdminDashboardPage.vue`; pass slices of data as props to three focused card components.

---

## Standard Stack

The project uses no charting library and no heavy state manager — everything is in-component
with `ref`/`computed`. That pattern holds here. No new packages are needed.

### Core (already in project)

| Library | Purpose | Status |
|---------|---------|--------|
| Quasar `QCard` / `QCardSection` | Stat card layout | Available |
| `useErrorHandler` composable | Unified error display | Available |
| `normalizeLongIds` / `longToString` | ID safety | Available |
| vue-i18n `useI18n` | Translations | Available |

### No Charts Required

DASH-01 requests daily breakdown but does **not** require a chart — a compact table or
horizontal list inside a `QCard` suffices and avoids pulling in `chart.js` or `echarts`.
Daily breakdown is already structured as `List<DeliveryDailyStat>` from the backend.

**Installation:** nothing new to install.

---

## Existing Backend Endpoints That Feed the Dashboard

All are `GET`, all are `@PreAuthorize("hasRole('ADMIN')")`.

| Endpoint | Response Contract | Dashboard Use |
|----------|------------------|---------------|
| `GET /api/admin/clients` | `List<AdminClientDto>` (id, name, status, balance) | Active client count (filter by `status == ACTIVE`), total credits in system (sum `balance`) |
| `GET /api/admin/sms/analytics/delivery-stats` | `DeliveryStatsResponse` | total_sent, delivered, failed, delivery_rate, total_segments, daily_breakdown |
| `GET /api/admin/credits/summary` | `SpendSummaryResponse` | sms_debit, sms_refund, topup_approved, net_credits_consumed (top-up analysis) |
| `GET /api/admin/health/nexah/circuit-breaker` | `CircuitBreakerHealthResponse` | DASH-02: state, failure_rate_pct, failed_calls, successful_calls, not_permitted_calls |
| `GET /api/admin/health/nexah/provider-stats` | `ProviderStatsResponse` | total_submitted, dr_received, failed_count, failure_rate_pct (provider send aggregate) |
| `GET /api/admin/health/webhooks/stats` | `WebhookHealthResponse` | total_attempts, failure_count, exhausted_count (webhook delivery aggregates) |

**No new backend endpoint is needed.** All DASH-01 and DASH-02 requirements are covered.

### Response Field Reference

```
AdminClientDto:          id (Long), name, status (EntityStatus), balance (long)
DeliveryStatsResponse:   total_sent, delivered, failed, delivery_rate (double),
                         total_segments, daily_breakdown [ { date, total_sent, delivered, failed } ]
SpendSummaryResponse:    sms_debit, sms_refund, topup_approved, sms_reservation, net_credits_consumed
CircuitBreakerHealthResponse: state, failure_rate_pct, failed_calls, successful_calls, not_permitted_calls
ProviderStatsResponse:   total_submitted, dr_received, failed_count, failure_rate_pct
WebhookHealthResponse:   total_attempts, failure_count, exhausted_count
```

### Active Client Count and Total Credits

`GET /api/admin/clients` returns the full client list (no pagination — bounded for v1 per
`AdminClientResource` comment). Client-side derivation:

```js
const activeClients = computed(() => clients.value.filter(c => c.status === 'ACTIVE').length)
const totalCredits  = computed(() => clients.value.reduce((sum, c) => sum + c.balance, 0))
```

No new `count` or `sum` endpoint is required.

---

## New Backend Work Needed

None. All six endpoint mappings already exist and are reachable by admin. The `adminApi`
index (`src/api/admin/index.js`) does not yet have wrapper methods for:
- `/api/admin/sms/analytics/delivery-stats`
- `/api/admin/credits/summary`
- `/api/admin/health/nexah/circuit-breaker`
- `/api/admin/health/nexah/provider-stats`
- `/api/admin/health/webhooks/stats`

These five methods must be added to `src/api/admin/index.js`. The `/api/admin/clients` call
already exists as `adminApi.getClients()`.

---

## DashboardPage.vue Stub — Current State

File: `src/frontend/src/pages/admin/AdminDashboardPage.vue`

```vue
<template>
  <q-page class="q-pa-md">
    <div class="text-h5 q-mb-md">{{ t('admin.nav.dashboard') }}</div>
    <p class="text-grey-7">Loading...</p>
  </q-page>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
```

12 lines. The stub uses the existing i18n key `admin.nav.dashboard`. No state, no API calls,
no components. A full replacement is warranted; the planner should treat this as a rewrite
rather than an edit.

---

## Architecture Patterns

### Recommended Component Breakdown

The 250-line cap requires decomposition. With six API calls and multiple display groups,
AdminDashboardPage.vue would exceed 250 lines if written monolithically.

**Proposed split:**

```
src/
├── pages/admin/
│   └── AdminDashboardPage.vue       # orchestrator: fires 6 loads, owns all state, renders 3 cards
├── components/admin/
│   ├── DashboardSmsCard.vue         # delivery stats + daily breakdown table
│   ├── DashboardBillingCard.vue     # credit summary + active clients + total credits
│   └── DashboardSystemCard.vue      # circuit breaker chip + provider stats + webhook stats
```

**AdminDashboardPage.vue responsibilities (~120 lines):**
- `onMounted` fires all 6 loads via `Promise.all`
- Owns all reactive state refs
- Single `isLoading` ref (true while any load is pending)
- `useErrorHandler` for any failed call
- Renders page title, optional loading bar, three card components with props
- Single "Refresh" button that re-fires all loads

**Child card components (~60-80 lines each):**
- Receive stats as props (no API calls inside cards)
- Pure display: QCard, QCardSection, stat rows
- No loading state of their own (page handles it)

### Circuit Breaker Display Pattern (DASH-02)

The `state` field from `CircuitBreakerHealthResponse` is one of the Resilience4j strings:
`CLOSED`, `OPEN`, `HALF_OPEN`. Map to Quasar badge colors:

```js
const cbColorMap = {
  CLOSED:    'positive',   // green — healthy
  HALF_OPEN: 'warning',    // orange — recovering
  OPEN:      'negative',   // red — tripped
}
```

Display as a prominent `QBadge` at the top of DashboardSystemCard. This is the primary
health signal the admin sees at a glance (DASH-02).

### Parallel Loading Pattern

```js
// AdminDashboardPage.vue
const isLoading = ref(false)

async function loadAll() {
  isLoading.value = true
  clearError()
  try {
    const [clientsData, smsData, creditsData, cbData, providerData, webhookData] =
      await Promise.all([
        adminApi.getClients(),
        adminApi.getDeliveryStats(),
        adminApi.getCreditSummary(),
        adminApi.getCircuitBreakerHealth(),
        adminApi.getProviderStats(),
        adminApi.getWebhookHealth(),
      ])
    clients.value  = clientsData
    smsStats.value = smsData
    // ... etc
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadAll)
```

This matches the `QInnerLoading` loading pattern from FOUND-05.

### Stat Row Pattern (no chart library)

Use simple label + value rows inside `QCard` / `QCardSection`. The daily breakdown table is
a compact `QMarkupTable` with at most 7 rows (last 7 days), matching the table pattern in
SmsMonitorPage and WebhooksPage.

```vue
<!-- Simple stat row inside QCardSection -->
<div class="row justify-between q-py-xs">
  <span class="text-grey-7">{{ t('admin.dashboard.deliveryRate') }}</span>
  <span class="text-weight-medium">{{ formatPct(smsStats.delivery_rate) }}</span>
</div>
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Error display | Custom error component | `useErrorHandler` + `q-banner` (existing pattern) |
| Loading state | Custom spinner logic | `QInnerLoading` / `:loading` prop (FOUND-05) |
| Stat card layout | Custom CSS grid | `QCard` + `QCardSection` in a `row q-col-gutter-md` grid |
| Badge coloring | Custom CSS | Quasar `QBadge :color` |
| Async parallel | chained `.then()` | `Promise.all` |

---

## Common Pitfalls

### Pitfall 1: Calling clients endpoint to derive totals adds a 7th call

**What goes wrong:** Planner adds a separate `/api/admin/credits/total` endpoint or counts
clients via a dedicated stats endpoint.

**Prevention:** `getClients()` already returns every client with its balance. Derive
`activeClients` and `totalCredits` client-side via `filter` and `reduce` on the same array.

### Pitfall 2: AdminDashboardPage.vue grows past 250 lines

**What goes wrong:** All display logic stays in the page instead of in card components.

**Prevention:** The three card components are mandatory, not optional. Each card receives
only its own slice of data as props.

### Pitfall 3: ID guard on client balances

**What goes wrong:** Client `id` is a Java `Long`. If displayed, it must go through
`longToString`. Client `balance` is numeric and displayed as a number — do not coerce it to
string.

**Prevention:** When mapping client list: `normalizeLongIds(c, ['id'])` only.

### Pitfall 4: delivery_rate is a raw double (0.0–1.0)

**What goes wrong:** Displaying `delivery_rate` raw shows `0.9735` instead of `97.4%`.

**Prevention:** Format as `(value * 100).toFixed(1) + '%'` in a local `formatPct` function
inside the SMS card.

### Pitfall 5: Missing adminApi methods block both tasks

**What goes wrong:** The five new `adminApi` methods are added in the same task as the
frontend build, creating a dependency that makes the task too large.

**Prevention:** First task adds only the five `adminApi` methods. Second task builds the
dashboard components. Third task adds i18n keys.

---

## I18n Keys

### Already Exist

```js
// en-US and fr-FR both have:
admin.nav.dashboard  // 'Admin Dashboard' / 'Tableau de bord admin'
```

No other dashboard-specific keys exist.

### Keys to Add

All keys go under `admin.dashboard.*` in both `en-US/index.js` and `fr-FR/index.js`.

**Stat labels:**
```
admin.dashboard.title             // 'System Overview'
admin.dashboard.refresh           // 'Refresh'
admin.dashboard.lastUpdated       // 'Last updated: {time}'

// SMS stats card
admin.dashboard.smsCard           // 'SMS Statistics'
admin.dashboard.totalSent         // 'Total Sent'
admin.dashboard.delivered         // 'Delivered'
admin.dashboard.failed            // 'Failed'
admin.dashboard.deliveryRate      // 'Delivery Rate'
admin.dashboard.totalSegments     // 'Total Segments'
admin.dashboard.dailyBreakdown    // 'Daily Breakdown'
admin.dashboard.date              // 'Date'

// Billing / credits card
admin.dashboard.billingCard       // 'Credits & Clients'
admin.dashboard.activeClients     // 'Active Clients'
admin.dashboard.totalCredits      // 'Total Credits (XAF)'
admin.dashboard.smsDebit          // 'SMS Debit'
admin.dashboard.smsRefund         // 'SMS Refund'
admin.dashboard.topupApproved     // 'Topup Approved'
admin.dashboard.netConsumed       // 'Net Consumed'

// System health card
admin.dashboard.systemCard        // 'System Health'
admin.dashboard.circuitBreaker    // 'Circuit Breaker'
admin.dashboard.cbStateClosed     // 'Closed (Healthy)'
admin.dashboard.cbStateOpen       // 'Open (Tripped)'
admin.dashboard.cbStateHalfOpen   // 'Half-Open (Recovering)'
admin.dashboard.failureRate       // 'Failure Rate'
admin.dashboard.providerStats     // 'Provider (Nexah)'
admin.dashboard.totalSubmitted    // 'Submitted'
admin.dashboard.drReceived        // 'DRs Received'
admin.dashboard.webhookStats      // 'Webhooks'
admin.dashboard.totalAttempts     // 'Total Attempts'
admin.dashboard.failureCount      // 'Failures'
admin.dashboard.exhaustedCount    // 'Exhausted'
```

---

## Code Examples

### Adding adminApi Methods (api/admin/index.js)

```js
// Source: pattern from existing adminApi methods
getDeliveryStats(params = {}) {
  return api.get('/api/admin/sms/analytics/delivery-stats', { params })
},
getCreditSummary(params = {}) {
  return api.get('/api/admin/credits/summary', { params })
},
getCircuitBreakerHealth() {
  return api.get('/api/admin/health/nexah/circuit-breaker')
},
getProviderStats() {
  return api.get('/api/admin/health/nexah/provider-stats')
},
getWebhookHealth() {
  return api.get('/api/admin/health/webhooks/stats')
},
```

### Circuit Breaker Badge in DashboardSystemCard.vue

```vue
<q-badge
  :color="cbColorMap[circuitBreaker.state] ?? 'grey'"
  :label="cbStateLabel(circuitBreaker.state)"
  class="text-subtitle2"
/>
```

```js
const cbColorMap = { CLOSED: 'positive', HALF_OPEN: 'warning', OPEN: 'negative' }
function cbStateLabel(state) {
  const map = {
    CLOSED:    t('admin.dashboard.cbStateClosed'),
    OPEN:      t('admin.dashboard.cbStateOpen'),
    HALF_OPEN: t('admin.dashboard.cbStateHalfOpen'),
  }
  return map[state] ?? state
}
```

### Delivery Rate Formatter

```js
// Inside DashboardSmsCard.vue
function formatPct(rate) {
  if (rate == null) return '—'
  return (rate * 100).toFixed(1) + '%'
}
```

### Active Client Count from Client List

```js
// Inside AdminDashboardPage.vue (computed)
const activeClientCount = computed(() =>
  clients.value.filter(c => c.status === 'ACTIVE').length
)
const totalCredits = computed(() =>
  clients.value.reduce((sum, c) => sum + c.balance, 0)
)
```

---

## Task Breakdown

Recommended three tasks to stay within complexity and 250-line limits:

| Task | Deliverable | Est. Lines |
|------|-------------|-----------|
| 17-01 | Add 5 adminApi methods to `api/admin/index.js`; no frontend components | ~25 new lines |
| 17-02 | Build 3 child card components (DashboardSmsCard, DashboardBillingCard, DashboardSystemCard) | ~60-80 lines each |
| 17-03 | Rewrite AdminDashboardPage.vue to orchestrate loads and render cards; add all i18n keys | ~120 lines page + i18n |

Tasks 17-02 and 17-03 depend on 17-01. Tasks 17-02 and 17-03 can be split further if needed,
but three tasks is the natural seam.

---

## Open Questions

1. **Daily breakdown date range**
   - What we know: `delivery_rate` endpoint accepts optional `from`/`to` params; no param = all time.
   - What's unclear: Does the dashboard show all-time stats or last 7/30 days? The requirements
     do not specify a time window for daily breakdown.
   - Recommendation: Default to no filter (all-time). Add a note in the plan task to confirm
     with product if a default 30-day window is expected.

2. **Refresh strategy**
   - What we know: No WebSocket or SSE infrastructure exists in the frontend.
   - What's unclear: Should the dashboard auto-refresh on a timer?
   - Recommendation: Manual refresh button only (DASH-01/DASH-02 do not mention auto-refresh).

---

## Sources

### Primary (HIGH confidence)

- Direct codebase inspection — all backend Java contracts and resource classes read verbatim
- `src/frontend/src/api/admin/index.js` — verified existing adminApi methods
- `src/frontend/src/pages/admin/AdminDashboardPage.vue` — confirmed stub state
- `src/frontend/src/i18n/en-US/index.js` + `fr-FR/index.js` — confirmed existing and missing keys
- All six backend resource classes (`AdminNexahHealthResource`, `AdminSmsAnalyticsResource`,
  `AdminCreditResource`, `AdminTopupResource`, `AdminWebhookHealthResource`,
  `AdminClientResource`) read directly

### Secondary (MEDIUM confidence)

- Resilience4j state string values (`CLOSED`, `OPEN`, `HALF_OPEN`) — from training data,
  consistent with the field name `state` in `CircuitBreakerHealthResponse`. These are the
  standard Resilience4j enum names; the backend uses Resilience4j per the circuit breaker
  contract existing in Phase 10.

---

## Metadata

**Confidence breakdown:**

| Area | Level | Reason |
|------|-------|--------|
| Existing endpoints and contracts | HIGH | Read directly from Java source |
| Frontend component pattern | HIGH | Read directly from WebhooksPage.vue and SmsMonitorPage.vue |
| i18n keys present vs missing | HIGH | Read directly from both locale files |
| Circuit breaker state strings | MEDIUM | Backend record field exists; state string values from Resilience4j convention |
| 250-line component split | HIGH | Verified against actual WebhooksPage (234 lines) as reference |

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (no fast-moving dependencies; all findings from internal codebase)
