# Phase 15: Top-up Management — Research

**Researched:** 2026-03-12
**Domain:** Vue 3 / Quasar admin page — approve/reject workflow with tabbed list view
**Confidence:** HIGH (all findings from direct codebase inspection)

---

## Summary

Phase 15 requires turning the stub `TopupsPage.vue` into a functional admin page. The backend is fully implemented: three endpoints exist under `/api/admin/topups` — a filterable history endpoint and PUT approve/reject per topup. There are no pending-specific endpoints; pending requests are retrieved via the history endpoint with `?topupStatus=PENDING_APPROVAL`.

The `TopupHistoryItem` DTO returns a raw `long id` (not the `top_XXX` string). The frontend must construct the topup ID for approve/reject calls as `"top_" + item.id`. The `id` field is a Long that could exceed JS MAX_SAFE_INTEGER, so `longToString()` must be applied to it.

There is no `client_name` in the history response — only `client_id`. The UI must display client IDs (formatted as strings). The page has one backend data source (`/api/admin/topups/history`) and two actions (approve, reject), both of which are per-row inline buttons.

**Primary recommendation:** Build one page with two tabs (Pending / History). Tab 1 calls the history endpoint filtered to `PENDING_APPROVAL` and shows Approve + Reject buttons. Tab 2 calls the history endpoint unfiltered (or filtered to APPROVED/REJECTED) and shows status only. All patterns follow the ClientsPage / ApiKeysDialog precedent.

---

## Standard Stack

All of these are already in the project — no new dependencies.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vue 3 Composition API | project-standard | `<script setup>` component authoring | Established pattern in all admin pages |
| Quasar Framework | project-standard | `q-page`, `q-tabs`, `q-markup-table`, `q-btn`, `q-banner`, `q-inner-loading`, `q-badge` | Already used across all admin pages |
| vue-i18n | project-standard | `useI18n()` / `t()` | Full i18n required (en-US + fr-FR) |
| useErrorHandler | project composable | API error surfacing | FOUND-07 mandate |
| adminApi | `src/api/admin/index.js` | Centralized API calls | FOUND-06 mandate |
| longToString / normalizeLongIds | `src/utils/longToString.js` | Safe Long-to-String conversion | Long ID guard decision |

### Supporting
| Library | Purpose | When to Use |
|---------|---------|-------------|
| `useQuasar` (`$q.notify`) | Success toasts after approve/reject | After successful action |
| `ref`, `computed`, `onMounted` | Reactive state | Throughout |

**Installation:** None — all dependencies are present.

---

## Backend API Contract

**Confidence: HIGH** — verified from `AdminTopupResource.java`, `TopupHistoryItem.java`, `TopupStatusResponse.java`.

### Endpoints

| Method | Path | Purpose | Request | Response |
|--------|------|---------|---------|----------|
| GET | `/api/admin/topups/history` | Fetch all topups (filterable) | query params | `TopupHistoryResponse` |
| PUT | `/api/admin/topups/{topup_id}/approve` | Approve a pending topup | path param `top_XXX` | `TopupStatusResponse` |
| PUT | `/api/admin/topups/{topup_id}/reject` | Reject a pending topup | path param `top_XXX` | `TopupStatusResponse` |

### GET /api/admin/topups/history — Query Parameters (all optional)

| Param | Type | Purpose | Example |
|-------|------|---------|---------|
| `clientId` | Long | Filter by client | `?clientId=42` |
| `topupStatus` | String | Filter by status | `?topupStatus=PENDING_APPROVAL` |
| `from` | ISO-8601 Instant | Created date lower bound | `?from=2026-01-01T00:00:00Z` |
| `to` | ISO-8601 Instant | Created date upper bound | |

**To get pending requests:** `GET /api/admin/topups/history?topupStatus=PENDING_APPROVAL`
**To get history (processed):** `GET /api/admin/topups/history` (all), or add `?topupStatus=APPROVED` / `?topupStatus=REJECTED`

### TopupHistoryResponse shape (JSON)

```json
{
  "topups": [
    {
      "id": 1234,
      "client_id": 56,
      "amount": 5000,
      "transaction_id": "TXN-001",
      "payment_type": "MOBILE_MONEY",
      "account_number": "677000001",
      "topup_status": "PENDING_APPROVAL",
      "created_date": "2026-03-10T09:00:00Z",
      "approved_at": null,
      "rejected_at": null
    }
  ]
}
```

**Important:** `id` is a raw `long` — NOT the `top_XXX` string. To call approve/reject, the frontend constructs `"top_" + item.id`. Apply `longToString()` to `id` and `client_id` immediately after fetch.

### TopupStatusResponse shape (JSON, from approve/reject)

```json
{
  "topup_id": "top_1234",
  "amount": 5000,
  "status": "APPROVED",
  "approved_at": "2026-03-12T10:00:00Z"
}
```

### Error: 409 TOPUP_ALREADY_PROCESSED

If admin tries to approve/reject a topup that is already APPROVED or REJECTED, backend returns HTTP 409. The `useErrorHandler` composable handles this and the error key is `"TOPUP_ALREADY_PROCESSED"`. An i18n key should be added for it, or the fallback raw message will be shown.

---

## TopupStatus Enum Values

**Confidence: HIGH** — from `TopupStatus.java`.

| Value | Meaning |
|-------|---------|
| `PENDING_APPROVAL` | Awaiting admin decision (action buttons shown) |
| `APPROVED` | Admin approved — client balance was credited |
| `REJECTED` | Admin rejected — balance unchanged |

---

## Architecture Patterns

### Page Structure: Two-Tab Approach

The single `TopupsPage.vue` serves both TOUP-01 (pending) and TOUP-04 (history) using Quasar tabs:

```
TopupsPage.vue
├── Tab 1: "Pending" — loads history?topupStatus=PENDING_APPROVAL, shows Approve + Reject per row
└── Tab 2: "History" — loads history (all or APPROVED+REJECTED), shows status badge, no action buttons
```

Alternative single-list approach with status filter dropdown is also viable, but two tabs makes the admin workflow clearer and matches UX expectations.

### Recommended Project Structure

```
src/
├── api/admin/index.js          — add getTopupHistory(), approveTopup(topupId), rejectTopup(topupId)
├── pages/admin/TopupsPage.vue  — replace stub with full implementation
└── i18n/
    ├── en-US/index.js          — add admin.topups.* keys
    └── fr-FR/index.js          — add admin.topups.* (French) keys
```

No new component files required unless dialogs are needed (they are not — actions are inline buttons on rows).

### Pattern: Inline Approve/Reject (no dialog)

Approve and reject are irreversible but low-risk from a UX standpoint (admin is explicitly managing requests). The existing ApiKeysDialog uses inline revoke buttons with per-row loading maps. Apply the same pattern:

```javascript
// Per-row loading maps (same as isRevoking in ApiKeysDialog)
const isApproving = ref({})   // keyed by topupId string
const isRejecting = ref({})   // keyed by topupId string
```

Buttons disabled while a row's action is in-flight. Both buttons disabled when `topup_status !== 'PENDING_APPROVAL'` (defensive — tab filter already handles this).

### Pattern: statusLabelMap for TopupStatus

Follows the exact pattern from `ClientsPage.vue`:

```javascript
const statusLabelMap = {
  PENDING_APPROVAL: () => t('admin.topups.statusPending'),
  APPROVED:         () => t('admin.topups.statusApproved'),
  REJECTED:         () => t('admin.topups.statusRejected'),
}
```

### Pattern: Tab State

```javascript
const activeTab = ref('pending')  // 'pending' | 'history'

// Load appropriate data on tab change
watch(activeTab, loadForTab)
onMounted(loadForTab)

function loadForTab() {
  if (activeTab.value === 'pending') loadPending()
  else loadHistory()
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Error surfacing | Custom error state | `useErrorHandler()` | FOUND-07 mandate; already handles `setError/clearError/hasError/errorMessage` |
| API calls | Inline `api.put(...)` in component | `adminApi.approveTopup()` etc. in `src/api/admin/index.js` | FOUND-06 mandate |
| Loading overlay | Custom CSS spinner | `q-inner-loading` with `q-spinner-dots` | Quasar standard, already used in ClientsPage |
| Long ID safety | Custom serialization | `longToString()` / `normalizeLongIds()` from `src/utils/longToString.js` | Established project utility |
| Success notifications | `alert()` or custom | `$q.notify({ type: 'positive', message: t(...) })` | Quasar notify, same as ApiKeysDialog |

**Key insight:** All four utilities (error handler, API module, loading overlay, Long ID guard) are established project patterns that must be used — not reimplemented.

---

## Common Pitfalls

### Pitfall 1: Using raw `id` Long as topup_id in API call

**What goes wrong:** Calling `PUT /api/admin/topups/1234/approve` instead of `PUT /api/admin/topups/top_1234/approve` — backend returns 404 because `parseTopupId()` requires the `top_` prefix.

**Why it happens:** `TopupHistoryItem.id` is a raw `long`, not the formatted ID. The `top_` prefix is only applied in `TopupService.formatTopupId()`, and that value is NOT returned in the history response.

**How to avoid:** After normalizing Long IDs, construct `topupId = "top_" + item.id` and store it on the item. Use this field for all approve/reject calls.

**Warning signs:** 404 errors on approve/reject in testing.

### Pitfall 2: Forgetting Long ID guard on `id` and `client_id`

**What goes wrong:** Large client IDs silently truncated by `JSON.parse` before `longToString()` can help — displays wrong ID.

**How to avoid:** Apply `normalizeLongIds(item, ['id', 'client_id'])` immediately after the API response, in the api function or in `loadPending()`/`loadHistory()`.

### Pitfall 3: Single shared loading boolean causing both tabs to lock

**What goes wrong:** Using one `isLoading` ref across both tabs — approving in Tab 1 shows the spinner in Tab 2.

**How to avoid:** Use separate `isPendingLoading` and `isHistoryLoading` booleans, or a single `isLoading` scoped to the active tab's section only.

### Pitfall 4: No error clear between tab switches or retries

**What goes wrong:** Old error banner persists when switching tabs or re-loading.

**How to avoid:** Call `clearError()` at the start of every load function — same pattern as `ClientsPage.loadClients()`.

### Pitfall 5: Missing i18n key for TOPUP_ALREADY_PROCESSED

**What goes wrong:** Trying to approve an already-processed topup shows raw backend message instead of translated string.

**Why it happens:** `useErrorHandler.errorMessage` tries `t(errorKey)` but falls back to raw message if key absent.

**How to avoid:** Add `topupAlreadyProcessed` i18n key — or accept the fallback (the backend message is in English only). Recommend adding the key.

---

## Code Examples

### API Module Addition

```javascript
// src/api/admin/index.js — add to adminApi object
getTopupHistory(params = {}) {
  // params: { topupStatus, clientId, from, to } — all optional
  return api.get('/api/admin/topups/history', { params })
},
approveTopup(topupId) {
  // topupId must be "top_XXX" format
  return api.put(`/api/admin/topups/${topupId}/approve`)
},
rejectTopup(topupId) {
  return api.put(`/api/admin/topups/${topupId}/reject`)
},
```

### Loading Pending Topups

```javascript
async function loadPending() {
  isPendingLoading.value = true
  clearError()
  try {
    const data = await adminApi.getTopupHistory({ topupStatus: 'PENDING_APPROVAL' })
    pendingTopups.value = data.topups.map((item) => ({
      ...normalizeLongIds(item, ['id', 'client_id']),
      topupId: 'top_' + item.id,   // pre-build the formatted ID for approve/reject
    }))
  } catch (err) {
    setError(err)
  } finally {
    isPendingLoading.value = false
  }
}
```

### Approve Action with Per-Row Loading

```javascript
// Pattern mirrors revokeKey() in ApiKeysDialog.vue
async function approveTopup(topupId) {
  isApproving.value = { ...isApproving.value, [topupId]: true }
  clearError()
  try {
    await adminApi.approveTopup(topupId)
    $q.notify({ type: 'positive', message: t('admin.topups.approved') })
    await loadPending()         // refresh pending list
  } catch (err) {
    setError(err)
  } finally {
    const updated = { ...isApproving.value }
    delete updated[topupId]
    isApproving.value = updated
  }
}
```

### Status Badge Colors

```javascript
const statusColorMap = {
  PENDING_APPROVAL: 'warning',
  APPROVED:         'positive',
  REJECTED:         'negative',
}
```

### Quasar Tabs Template Skeleton

```html
<q-tabs v-model="activeTab" align="left" class="q-mb-md">
  <q-tab name="pending" :label="t('admin.topups.tabPending')" />
  <q-tab name="history" :label="t('admin.topups.tabHistory')" />
</q-tabs>

<q-tab-panels v-model="activeTab" animated>
  <q-tab-panel name="pending"> ... </q-tab-panel>
  <q-tab-panel name="history"> ... </q-tab-panel>
</q-tab-panels>
```

---

## I18n Keys Required

**Confidence: HIGH** — inspected en-US/index.js; none of these exist yet.

### English (en-US) additions to `admin` section

```javascript
topups: {
  title: 'Top-up Requests',
  tabPending: 'Pending',
  tabHistory: 'History',
  // Table columns
  id: 'Topup ID',
  clientId: 'Client ID',
  amount: 'Amount (XAF)',
  transactionId: 'Transaction ID',
  paymentType: 'Payment Type',
  accountNumber: 'Account Number',
  requestDate: 'Requested',
  processedDate: 'Processed',
  status: 'Status',
  // Status values
  statusPending: 'Pending',
  statusApproved: 'Approved',
  statusRejected: 'Rejected',
  // Actions
  approve: 'Approve',
  reject: 'Reject',
  // Notifications
  approved: 'Top-up approved — client balance updated',
  rejected: 'Top-up rejected',
  // Empty states
  noPending: 'No pending top-up requests',
  noHistory: 'No top-up history found',
  // Error
  topupAlreadyProcessed: 'This top-up has already been processed',
}
```

### French (fr-FR) additions to `admin` section

```javascript
topups: {
  title: 'Demandes de recharge',
  tabPending: 'En attente',
  tabHistory: 'Historique',
  id: 'ID recharge',
  clientId: 'ID client',
  amount: 'Montant (XAF)',
  transactionId: 'ID transaction',
  paymentType: 'Mode de paiement',
  accountNumber: 'Numero de compte',
  requestDate: 'Demandee le',
  processedDate: 'Traitee le',
  status: 'Statut',
  statusPending: 'En attente',
  statusApproved: 'Approuvee',
  statusRejected: 'Rejetee',
  approve: 'Approuver',
  reject: 'Rejeter',
  approved: 'Recharge approuvee — solde client mis a jour',
  rejected: 'Recharge rejetee',
  noPending: 'Aucune demande de recharge en attente',
  noHistory: 'Aucun historique de recharge',
  topupAlreadyProcessed: 'Cette recharge a deja ete traitee',
}
```

---

## Existing i18n Already Present

The following key already exists and is used by the page header:

```javascript
admin.nav.topups  // en: 'Top-ups'  fr: 'Recharges'
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Stub `TopupsPage.vue` (static "Loading...") | Full two-tab page with approve/reject workflow | Phase 15 replaces the stub entirely |
| No topup API methods in `adminApi` | Three new methods added to `src/api/admin/index.js` | Centralized per FOUND-06 |

---

## Open Questions

1. **History tab filtering scope**
   - What we know: History endpoint accepts optional `topupStatus` filter
   - What's unclear: Should history tab show ALL statuses or only APPROVED + REJECTED (excluding pending ones that also appear in pending tab)?
   - Recommendation: History tab should filter to APPROVED and REJECTED only to avoid duplication with the pending tab. Use two separate calls or a single call with status cycling. Simplest: one call without status filter, then client-side exclude PENDING_APPROVAL rows in history tab. Or call with `topupStatus` not set and let the history tab show everything (including pending — duplicates but clear).
   - **Decision for planner:** Recommend history tab calls history endpoint with no status filter (all records visible for audit). Pending tab calls with `topupStatus=PENDING_APPROVAL` only.

2. **Client name display**
   - What we know: `TopupHistoryItem` has `client_id` (Long) only — no `client_name`
   - What's unclear: Should the admin see client names alongside IDs?
   - Recommendation: Display `client_id` as a string. Do NOT fetch client list to join names — no requirement for it and it adds complexity. The admin can cross-reference from the Clients page.

3. **Pagination**
   - What we know: The history endpoint returns all matching records with no pagination parameters. `TopupHistoryResponse` is a simple list wrapper with no `totalPages`/`totalElements`.
   - What's unclear: Could be large in production.
   - Recommendation: No pagination needed for Phase 15. The backend does not support it. If the list is long, client-side filter by clientId string is sufficient.

---

## Sources

### Primary (HIGH confidence)
- Direct file read: `src/main/java/com/softropic/sendam/gateway/billing/api/AdminTopupResource.java` — endpoint paths, HTTP methods, response types
- Direct file read: `src/main/java/com/softropic/sendam/gateway/billing/contract/TopupHistoryItem.java` — exact JSON field names and types
- Direct file read: `src/main/java/com/softropic/sendam/gateway/billing/contract/TopupStatusResponse.java` — approve/reject response shape
- Direct file read: `src/main/java/com/softropic/sendam/gateway/billing/contract/TopupStatus.java` — all status values
- Direct file read: `src/main/java/com/softropic/sendam/gateway/billing/service/TopupService.java` — `top_` prefix requirement, id construction
- Direct file read: `src/frontend/src/pages/admin/ClientsPage.vue` — canonical admin page pattern to replicate
- Direct file read: `src/frontend/src/components/admin/ApiKeysDialog.vue` — per-row loading map, inline action pattern
- Direct file read: `src/frontend/src/api/admin/index.js` — existing api module to extend
- Direct file read: `src/frontend/src/i18n/en-US/index.js` — existing keys, gap analysis
- Direct file read: `src/frontend/src/i18n/fr-FR/index.js` — French translations needed

---

## Metadata

**Confidence breakdown:**
- Backend API contract: HIGH — read directly from Java sources
- Response DTO shapes: HIGH — read from record/interface definitions
- Frontend patterns: HIGH — read from existing Phase 14 implementations
- i18n gaps: HIGH — read both locale files; no topups section exists yet
- `top_` prefix requirement: HIGH — read from TopupService.parseTopupId()

**Research date:** 2026-03-12
**Valid until:** Stable — no external dependencies; valid until backend or frontend patterns change
