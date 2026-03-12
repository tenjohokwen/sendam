# Phase 14: Client & API Key Management - Research

**Researched:** 2026-03-12
**Domain:** Vue 3 / Quasar admin pages — client CRUD and API key lifecycle
**Confidence:** HIGH

---

## Summary

Phase 14 builds the real content for `ClientsPage.vue` (currently a stub) and adds API key management UI. The backend is fully implemented and ready: two REST controllers (`AdminClientResource`, `AdminApiKeyResource`) expose all required operations. All contract DTOs have been read and verified.

The frontend codebase has an established pattern: API calls in `api/<domain>/index.js`, errors via `useErrorHandler`, success notifications via `$q.notify()`, loading state via local `isSubmitting` refs or `useLoading`, and dialogs as separate `.vue` components under `components/admin/`. The `api/admin/index.js` file is a placeholder with comments saying "Phase 14 will populate this."

The `requiresAdmin` guard in the router currently only checks authentication (not role). Phase 14 is explicitly tasked with adding role-based conditional rendering to the sidebar. The router guard improvement (Pinia user store) is also in scope.

**Primary recommendation:** Build in this order: (1) populate `api/admin/index.js`, (2) build `ClientsPage.vue` as the main page, (3) add `CreateClientDialog.vue`, (4) add `ApiKeysDialog.vue` (nested API key management for a selected client), (5) add the `RawKeyDialog.vue` (show-once banner), (6) wire i18n, (7) add Pinia user store + sidebar `v-if="isAdmin"`.

---

## Backend API Surface

### Client Endpoints

| Method | Path | Request | Response | Notes |
|--------|------|---------|---------|-------|
| GET | `/api/admin/clients` | — | `AdminClientDto[]` | No pagination (bounded set) |
| POST | `/api/admin/clients` | `CreateClientRequest` | `CreateClientResponse` | 201 Created |

**`AdminClientDto`** (GET /api/admin/clients items):
```
{
  id: Long,           // longToString() required
  name: String,
  status: "ACTIVE" | "INACTIVE" | "DELETED",
  balance: long       // available credit balance, XAF centimes
}
```
Ordered by `createdDate DESC` (server-side).

**`CreateClientRequest`** (POST body):
```json
{
  "name": "...",       // @NotBlank, @Size(min=2, max=100)
  "keyLabel": "..."    // optional, @Size(max=100)
}
```

**`CreateClientResponse`** (POST 201 body):
```json
{
  "clientId": 12345,   // Long — longToString() required
  "apiKeyId": null,    // currently null (listener creates key asynchronously)
  "rawApiKey": null    // currently null — see note below
}
```

**CRITICAL FINDING:** `ClientService.createClient()` returns `new CreateClientResponse(client.getId(), null, null)` — both `apiKeyId` and `rawApiKey` are hardcoded to `null`. The raw API key is NOT returned by the client creation endpoint. The raw key is only available through the dedicated API key creation endpoint (`POST /api/admin/clients/{clientId}/keys`). The frontend must:
1. Create the client (POST /api/admin/clients) — gets `clientId` back.
2. Immediately call POST /api/admin/clients/{clientId}/keys to create the initial key.
3. Show the raw key from the second call.

Or — the creation form can be designed to skip the automatic first key, and the admin creates keys separately. Based on the `keyLabel` field in `CreateClientRequest`, the original intent was to create a key automatically, but the current implementation does not surface it. The safer approach: create the client, then show the API key panel where admin can generate the first key explicitly.

### API Key Endpoints

| Method | Path | Request | Response | Notes |
|--------|------|---------|---------|-------|
| GET | `/api/admin/clients/{clientId}/keys` | — | `ApiKeyDto[]` | No pagination |
| POST | `/api/admin/clients/{clientId}/keys` | `CreateKeyRequest` (optional body) | `ApiKeyCreationResult` | 201 Created; raw key shown once |
| DELETE | `/api/admin/clients/{clientId}/keys/{keyId}` | — | — | 204 No Content |

**`ApiKeyDto`** (list items — never includes raw key):
```
{
  id: Long,            // longToString() required
  label: String,       // may be null
  status: String,      // "ACTIVE" | "REVOKED" (matches EntityStatus values)
  createdDate: Instant // ISO 8601 string in JSON
}
```

**`CreateKeyRequest`** (POST body — optional):
```json
{ "label": "my-key" }  // label is optional; body itself is optional (required=false)
```
The controller accepts `@RequestBody(required = false)` — can POST with empty body.

**`ApiKeyCreationResult`** (POST 201 body — the ONE time raw key is visible):
```json
{
  "apiKeyId": 67890,   // Long — longToString() required
  "rawKey": "sk_..."   // Raw key value — show exactly once, never stored
}
```

### Long ID fields requiring `longToString()`

- `AdminClientDto.id`
- `CreateClientResponse.clientId`
- `ApiKeyDto.id`
- `ApiKeyCreationResult.apiKeyId`

Use `normalizeLongIds(obj, ['id'])` for list items; use `longToString(val)` for single scalar fields.

---

## Frontend Codebase State

### What exists

| File | State | Notes |
|------|-------|-------|
| `pages/admin/ClientsPage.vue` | Stub — 11 lines | Shows heading + "Loading..." static text |
| `pages/admin/TopupsPage.vue` | Stub | Same pattern |
| `pages/admin/AuditPage.vue` | Stub | Same pattern |
| `pages/admin/SmsMonitorPage.vue` | Stub | Same pattern |
| `pages/admin/WebhooksPage.vue` | Stub | Same pattern |
| `pages/admin/AdminDashboardPage.vue` | Stub | Same pattern |
| `api/admin/index.js` | Placeholder | Comments only; no real functions |
| `composables/useErrorHandler.js` | Complete | `setError`, `clearError`, `hasError`, `errorMessage`, `errorKey`, `helpCode`, `isValidationError`, `hasFieldErrors`, `hasFieldError(name)`, `getFieldError(name)` |
| `composables/useLoading.js` | Complete | `isLoading` (global axios pending count) |
| `components/common/ServerPagination.vue` | Complete | Not needed here — no pagination |
| `utils/longToString.js` | Complete | `longToString(val)`, `normalizeLongIds(obj, fields)` |
| `router/routes.js` | Complete | Admin routes defined; `requiresAdmin` guard is cookie-only check |
| `router/index.js` | Partial | `requiresAdmin` check only validates `isAuthenticated`; role check deferred to this phase |
| `layouts/MainLayout.vue` | Partial | Sidebar shows admin section with `<!-- TODO Phase 14: add v-if="isAdmin" -->` comment |
| `stores/index.js` | Pinia wired | No user store yet (only `example-store.js`) |

### Established Patterns (from ProfilePage + dialogs)

**Page pattern:**
```js
const { setError, clearError, hasError, errorMessage, helpCode } = useErrorHandler()
const data = ref(null)
const isLoading = ref(false)

onMounted(async () => { await loadData() })

async function loadData() {
  isLoading.value = true
  clearError()
  try { data.value = await someApi.getX() }
  catch (err) { setError(err) }
  finally { isLoading.value = false }
}
```

**Dialog pattern (from `UpdateInfoDialog.vue`):**
- Props: `modelValue: Boolean`, data props
- Emits: `update:modelValue`, `updated`
- `dialogVisible` computed with get/set mapping to `modelValue`
- `isSubmitting` ref for button `:loading`
- `useErrorHandler` for API errors
- `$q.notify({ type: 'positive', message: t('...') })` on success
- `clearError()` on close
- Error banner: `<q-banner v-if="hasError && !isValidationError">`
- Field errors: `:error="hasFieldError('name')"` + `:error-message="getFieldError('name')"`

**API file pattern (from `profile.api.js`):**
```js
import { api } from 'src/boot/axios'
export const adminApi = {
  getClients() { return api.get('/api/admin/clients') },
  createClient(data) { return api.post('/api/admin/clients', data) },
  // ...
}
```
Note: axios interceptor unwraps `response.data` — callers receive the DTO directly (not the axios response wrapper).

**Notify pattern:**
```js
$q.notify({ type: 'positive', message: t('admin.clients.created') })
$q.notify({ type: 'negative', message: t('admin.apiKeys.revoked') })
```

---

## Gap Analysis

### What phase 14 must build

1. **`api/admin/index.js`** — Replace placeholder with real functions:
   - `getClients()`
   - `createClient(data)`
   - `getApiKeys(clientId)`
   - `createApiKey(clientId, label?)`
   - `revokeApiKey(clientId, keyId)`

2. **`pages/admin/ClientsPage.vue`** — Replace stub with:
   - Client list table (id, name, status, balance, actions)
   - Search/filter input (client-side — see below)
   - "Register client" action button
   - "Manage keys" per-row action button
   - Loading state (`QInnerLoading` or `isLoading` with `q-spinner-dots`)
   - Error banner
   - `CreateClientDialog` integration
   - `ApiKeysDialog` integration

3. **`components/admin/CreateClientDialog.vue`** — New dialog:
   - Fields: `name` (required, 2-100 chars), `keyLabel` (optional, max 100 chars)
   - Lazy-rule validation (FOUND-08)
   - On success: emit `created`, close, notify
   - Note: does NOT show raw key (current backend returns null for rawApiKey)

4. **`components/admin/ApiKeysDialog.vue`** — New dialog showing keys for a client:
   - Receives `clientId` and `clientName` props
   - Lists all keys (id, label, status, createdDate)
   - "Generate new key" button → calls createApiKey → opens `RawKeyDialog`
   - "Revoke" button per key (disabled if already revoked)
   - Loading state per action

5. **`components/admin/RawKeyDialog.vue`** — Show-once raw key dialog:
   - Receives `rawKey` and `keyId` props (or v-model)
   - Displays key prominently with copy button
   - Warning: "This key will never be shown again"
   - No API call needed — just display what was received from createApiKey
   - On close: key is gone (component destroyed, value not stored anywhere)

6. **`stores/user.store.js`** — Pinia user store:
   - Stores `roles: []` and `username`
   - `isAdmin` computed getter
   - `fetchUser()` action calling the profile API (or dedicated endpoint)
   - Used by sidebar and router guard

7. **Router guard update** in `router/index.js`:
   - Import user store
   - On `requiresAdmin`: if authenticated but store not loaded, fetch user first
   - If user lacks `ROLE_ADMIN`, redirect to `/dashboard` with denial notice

8. **Sidebar update** in `MainLayout.vue`:
   - Add `v-if="userStore.isAdmin"` to admin nav section (per TODO comment)

### What phase 14 does NOT need to build

- No changes to ServerPagination (no paginated endpoints here)
- No changes to other admin stubs (TopupsPage, AuditPage etc. stay as stubs for phases 15-17)
- No new backend work — all endpoints are implemented

---

## Component Plan

### `api/admin/index.js`

```js
// Replaces placeholder
export const adminApi = {
  getClients() { return api.get('/api/admin/clients') },
  createClient(data) { return api.post('/api/admin/clients', data) },
  getApiKeys(clientId) { return api.get(`/api/admin/clients/${clientId}/keys`) },
  createApiKey(clientId, label) {
    return api.post(`/api/admin/clients/${clientId}/keys`, label ? { label } : undefined)
  },
  revokeApiKey(clientId, keyId) {
    return api.delete(`/api/admin/clients/${clientId}/keys/${keyId}`)
  }
}
```

### `ClientsPage.vue`

Props: none (page component)

State:
- `clients: ref([])` — full list (no pagination)
- `isLoading: ref(false)`
- `filterText: ref('')` — for client-side search
- `showCreateDialog: ref(false)`
- `selectedClient: ref(null)` — for ApiKeysDialog
- `showApiKeysDialog: ref(false)`

Computed:
- `filteredClients` — filters `clients` by `filterText` against `id` and `name`

Key template elements:
- `q-input` with `v-model="filterText"` + debounce/immediate (client-side filter, no API call)
- `q-btn` "Register client" → `showCreateDialog = true`
- `q-table` or manual `q-markup-table` with rows
- Per row: "Manage Keys" button → sets `selectedClient`, `showApiKeysDialog = true`
- `CreateClientDialog` + `ApiKeysDialog` mounted at bottom

### `CreateClientDialog.vue`

Props: `modelValue: Boolean`
Emits: `update:modelValue`, `created`

Form fields:
- `name`: `q-input`, required, lazy-rules, min 2 max 100
- `keyLabel`: `q-input`, optional, lazy-rules, max 100

On submit: `adminApi.createClient({ name, keyLabel })`
On success: `$q.notify(positive)`, emit `created`, close

### `ApiKeysDialog.vue`

Props: `modelValue: Boolean`, `clientId: String` (already longToString'd), `clientName: String`
Emits: `update:modelValue`

State:
- `keys: ref([])` — list of ApiKeyDto (normalized)
- `isLoading: ref(false)`
- `isGenerating: ref(false)`
- `isRevoking: ref({})` — map of keyId → boolean for per-row loading
- `rawKeyResult: ref(null)` — holds ApiKeyCreationResult after generation
- `showRawKeyDialog: ref(false)`

On open (watch modelValue): load keys via `adminApi.getApiKeys(clientId)`

Generate key: `adminApi.createApiKey(clientId, optionalLabel)` → store result in `rawKeyResult`, open `RawKeyDialog`, then refresh keys list

Revoke: `adminApi.revokeApiKey(clientId, keyId)` → refresh keys list → notify

### `RawKeyDialog.vue`

Props: `modelValue: Boolean`, `rawKey: String`, `keyId: String`
Emits: `update:modelValue`

Template:
- Warning banner: "This key will only be shown once. Copy it now."
- Large text/code display of `rawKey`
- Copy-to-clipboard button (use `navigator.clipboard.writeText`)
- Close button
- No form, no API calls

---

## Search / Filter Approach (CLNT-03)

**Decision: client-side filter.**

The GET /api/admin/clients endpoint returns all clients with no query params (confirmed by reading the controller — no `@RequestParam` for search). The backend was designed "No pagination — admin use only; client count is bounded for v1."

Implementation: reactive `computed` property that filters the full list in memory by comparing `filterText` against `client.id` (string) and `client.name` (case-insensitive). No debounce needed since the list is small. Reset page if using any paginator (not applicable here).

---

## API Key "Show Once" Pattern (AKEY-04)

The raw key arrives in `ApiKeyCreationResult.rawKey` from `POST /api/admin/clients/{clientId}/keys`. The value is never stored in the backend after creation (only a hashed version is stored). The frontend must:

1. Hold the raw key in a local `ref` only — never in a store, cookie, or localStorage.
2. Open `RawKeyDialog` with the raw key as a prop.
3. When `RawKeyDialog` closes, null out the local ref. The value is gone.
4. The `RawKeyDialog` must be `v-if` (not `v-show`) so it unmounts and cannot be re-opened with stale key data.

The same pattern applies to the initial key that _could_ be created during `createClient` — but since the backend currently returns `rawApiKey: null` from POST /api/admin/clients, that path is inert. Admin must generate a key explicitly via the ApiKeysDialog.

---

## Pinia User Store Question

**Does this phase need a user store?**

Yes. The router guard comment (Phase 13-03 decision) explicitly deferred this to Phase 14:

> "`requiresAdmin` guard defers full role check to Phase 14 (no Pinia user store yet)"

And the MainLayout.vue has:

> `<!-- TODO Phase 14: add v-if="isAdmin" when user store is available -->`

**What the store needs:**

The profile API already exists at `GET /api/account/profile` (used by `ProfilePage`). The response includes user data. We need to verify what role fields it returns.

Looking at `profileApi.getProfile()` — it returns a `UserDto`. The role/authority information needs to be surfaced. The simplest approach: after login or on first protected route access, fetch profile and store `roles`. If the response doesn't include roles, a separate `GET /api/account/roles` or similar may be needed.

**Risk:** The research could not confirm what fields `UserDto` returns for roles without reading the `UserDto` Java class. This is a LOW confidence gap — the planner should verify `UserDto` role fields before the store task.

**Minimal store structure:**
```js
// stores/user.store.js
export const useUserStore = defineStore('user', {
  state: () => ({ roles: [], username: '' }),
  getters: { isAdmin: (state) => state.roles.includes('ROLE_ADMIN') },
  actions: { async fetchUser() { /* call profile API */ } }
})
```

---

## i18n Keys Needed

### en-US additions (under `admin.clients` and `admin.apiKeys`)

```js
admin: {
  // existing keys preserved
  clients: {
    title: 'Clients',
    id: 'Client ID',
    name: 'Name',
    status: 'Status',
    balance: 'Balance',
    filterPlaceholder: 'Search by ID or name',
    registerClient: 'Register Client',
    manageKeys: 'Manage Keys',
    noClients: 'No clients found',
    createTitle: 'Register New Client',
    clientName: 'Client Name',
    keyLabel: 'Initial API Key Label (optional)',
    created: 'Client registered successfully',
    statusActive: 'Active',
    statusInactive: 'Inactive',
    statusDeleted: 'Deleted',
  },
  apiKeys: {
    title: 'API Keys for {name}',
    id: 'Key ID',
    label: 'Label',
    status: 'Status',
    createdDate: 'Created',
    generateKey: 'Generate New Key',
    revoke: 'Revoke',
    revokeConfirm: 'Revoke API key?',
    revoked: 'API key revoked',
    generated: 'API key generated',
    noKeys: 'No API keys found',
    rawKeyTitle: 'New API Key Created',
    rawKeyWarning: 'This key will only be shown once. Copy it now — it cannot be recovered.',
    copyKey: 'Copy Key',
    keyCopied: 'API key copied to clipboard',
    keyLabelOptional: 'Key Label (optional)',
  }
}
```

### fr-FR additions (parallel French translations)

All keys above need French equivalents. Examples:
```js
admin: {
  clients: {
    title: 'Clients',
    filterPlaceholder: 'Rechercher par ID ou nom',
    registerClient: 'Enregistrer un client',
    manageKeys: 'Gerer les cles',
    created: 'Client enregistre avec succes',
    // ...
  },
  apiKeys: {
    title: 'Cles API pour {name}',
    generateKey: 'Generer une nouvelle cle',
    revoke: 'Revoquer',
    rawKeyWarning: 'Cette cle ne sera affichee qu\'une seule fois. Copiez-la maintenant.',
    // ...
  }
}
```

---

## Common Pitfalls

### Pitfall 1: `rawApiKey` null from createClient

**What goes wrong:** Developer assumes the POST /api/admin/clients response contains the initial raw API key (as `rawApiKey` in `CreateClientResponse`). It is hardcoded to `null` in `ClientService`. The API key is created by an event listener asynchronously.

**How to avoid:** Do not show a "raw key" flow after client creation. Instruct admin to go to API Keys panel and generate a key explicitly via POST .../keys.

### Pitfall 2: Long IDs in URL path params

**What goes wrong:** `clientId` from `AdminClientDto.id` is a Java Long. When used in URL path for DELETE `/api/admin/clients/{clientId}/keys/{keyId}`, passing the numeric JS value can silently truncate if > MAX_SAFE_INTEGER.

**How to avoid:** Always call `longToString()` before storing IDs, and pass the string form directly into template literals for URL construction in `adminApi.js`.

### Pitfall 3: RawKeyDialog v-show vs v-if

**What goes wrong:** Using `v-show` means the component stays mounted even when not visible. The raw key prop remains accessible in the DOM.

**How to avoid:** Use `v-if` for RawKeyDialog. The component must unmount when closed so the raw key string is garbage collected.

### Pitfall 4: Missing `lazy-rules` on form fields (FOUND-08)

**What goes wrong:** Omitting `lazy-rules` on `q-input` causes validation errors to show immediately on page load, before the user interacts.

**How to avoid:** All `q-input` and `q-select` components in CreateClientDialog and key label inputs must use `:lazy-rules="true"` or `lazy-rules`.

### Pitfall 5: Action buttons not disabled during loading (UXST-04)

**What goes wrong:** Admin clicks "Revoke" twice quickly, submitting two DELETE requests.

**How to avoid:** Use per-row `isRevoking` map: `:disable="isRevoking[key.id]"` and `:loading="isRevoking[key.id]"`. For "Generate Key": `:disable="isGenerating"` + `:loading="isGenerating"`.

---

## Architecture Patterns

### Recommended File Structure for Phase 14

```
src/
├── api/
│   └── admin/
│       └── index.js          # Replace placeholder with real functions
├── components/
│   └── admin/                # NEW folder
│       ├── CreateClientDialog.vue
│       ├── ApiKeysDialog.vue
│       └── RawKeyDialog.vue
├── pages/
│   └── admin/
│       └── ClientsPage.vue   # Replace stub with real page
├── stores/
│   └── user.store.js         # NEW — Pinia store for role-based access
└── router/
    └── index.js              # Update requiresAdmin guard
```

### Data Flow

```
ClientsPage
  ├─ onMounted → adminApi.getClients() → normalize Long IDs → clients[]
  ├─ filterText → filteredClients (computed, client-side)
  ├─ "Register" → CreateClientDialog
  │   └─ submit → adminApi.createClient() → emit 'created' → reload
  └─ "Manage Keys" → ApiKeysDialog(clientId, clientName)
      ├─ onOpen → adminApi.getApiKeys(clientId) → normalize Long IDs → keys[]
      ├─ "Generate" → adminApi.createApiKey(clientId, label?) → rawKeyResult
      │   └─ rawKeyResult → RawKeyDialog(rawKey, keyId) [v-if]
      │       └─ close → null rawKeyResult → reload keys
      └─ "Revoke" → adminApi.revokeApiKey(clientId, keyId) → reload keys
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Admin section stubs with "Loading..." | Replace with real implementation | Phase 14 delivers first working admin page |
| `requiresAdmin` guard = cookie check only | Add Pinia user store + ROLE_ADMIN check | Sidebar hides admin nav for non-admins |

---

## Open Questions

1. **UserDto role fields**
   - What we know: `GET /api/account/profile` returns user data; `ProfilePage` uses it.
   - What's unclear: Does `UserDto` include `roles` or `authorities` as a list? The Java `UserDto` class was not read.
   - Recommendation: Read `UserDto.java` before writing the user store task. If roles are absent, a separate endpoint or store hydration strategy is needed.
   - File to check: `src/main/java/com/softropic/sendam/security/contract/UserDto.java` (likely path)

2. **Balance display format**
   - What we know: `balance` field is a `long` (XAF centimes based on project context).
   - What's unclear: Should it be displayed as raw integer (e.g., 10000) or formatted as currency (e.g., 100.00 XAF)?
   - Recommendation: Display as integer with "XAF" suffix for simplicity in v1; formatting can be upgraded later.

3. **createClient response: initial raw key strategy**
   - What we know: Backend returns `rawApiKey: null`. Frontend cannot show initial key from creation flow.
   - What's unclear: Was this intentional? The `keyLabel` field in `CreateClientRequest` suggests an initial key was planned.
   - Recommendation: Do not show raw key after client creation. Admin must generate key explicitly in the API Keys panel. Document this as known behavior.

---

## Sources

### Primary (HIGH confidence)
- Direct source read: `AdminClientResource.java` — endpoint paths, methods, DTOs
- Direct source read: `AdminApiKeyResource.java` — endpoint paths, methods, DTOs
- Direct source read: `AdminClientDto.java`, `CreateClientRequest.java`, `CreateClientResponse.java`
- Direct source read: `ApiKeyDto.java`, `ApiKeyCreationResult.java`, `CreateKeyRequest.java`
- Direct source read: `ClientService.java` — confirms rawApiKey is null in response
- Direct source read: `ClientsPage.vue`, `AuditPage.vue` — confirms stub state
- Direct source read: `api/admin/index.js` — confirms placeholder state
- Direct source read: `useErrorHandler.js`, `useLoading.js`, `longToString.js`
- Direct source read: `ServerPagination.vue`
- Direct source read: `UpdateInfoDialog.vue` — canonical dialog pattern
- Direct source read: `ProfilePage.vue` — canonical page pattern with error handling
- Direct source read: `router/index.js` — confirms `requiresAdmin` is cookie-only
- Direct source read: `MainLayout.vue` — confirms TODO comment for Phase 14
- Direct source read: `i18n/en-US/index.js`, `i18n/fr-FR/index.js` — existing keys
- Direct source read: `stores/index.js`, `example-store.js` — Pinia wired, no user store yet
- Direct source read: `boot/axios.js` — confirms interceptor unwraps `response.data`

### Tertiary (LOW confidence — needs validation)
- UserDto role fields: not read; inferred from context

---

## Metadata

**Confidence breakdown:**
- Backend API surface: HIGH — all DTOs and controllers read directly
- Frontend codebase state: HIGH — all relevant files read directly
- Component plan: HIGH — directly derived from verified patterns
- i18n keys: HIGH — derived from requirements + existing key structure
- Pinia user store: MEDIUM — pattern is clear; UserDto fields unverified
- Search approach: HIGH — confirmed GET endpoint has no query params

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (stable domain — no fast-moving dependencies)
