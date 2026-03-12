# Phase 13: Foundation Extension - Research

**Researched:** 2026-03-12
**Domain:** Vue 3 + Quasar frontend scaffold extension
**Confidence:** HIGH

## Summary

The existing scaffold at `src/frontend/` is a working Vue 3 + Quasar application built with `<script setup>`, plain JavaScript, vue-i18n 11, axios, pinia, and vue-router 4. It already implements most of the FOUND/UXST patterns required by Phase 13 — the task is not to build from scratch but to audit, extend, and add what is explicitly missing.

The scaffold was built for the security/account module (login, registration, profile, session management). It lacks: admin routing and navigation, a Long→String conversion utility, a reusable server-side pagination component, and `$q.notify()` usage for success/error feedback. The loading and error handling infrastructure is well-established and needs to be carried forward rather than rebuilt.

Three concrete deliverables are needed: (1) a `utils/longToString.js` utility, (2) a `components/common/ServerPagination.vue` component, (3) admin route group added to the router with a `requiresAdmin` meta guard. The fourth deliverable is a decision log confirming all FOUND/UXST patterns are verified in the existing codebase.

**Primary recommendation:** Extend — do not rewrite. Add the three missing pieces, add admin routes, add the proxy entry for `/api/admin/**`, and document verified patterns.

---

## What Already Exists

### Directory Structure

```
src/frontend/src/
├── api/
│   ├── index.js              — exports all api modules
│   ├── auth.api.js           — login, logout, OTP, checkAuth
│   ├── account.api.js        — register, activate, reset password
│   ├── profile.api.js        — getProfile, updateEmail, updatePassword, etc.
│   └── session.api.js        — refresh
├── boot/
│   ├── axios.js              — axios instance, request/response interceptors, loading state pub-sub
│   └── i18n.js               — createI18n, getCurrentLocale()
├── components/
│   └── common/
│       ├── GlobalLoadingBar.vue   — q-linear-progress tied to pending requests
│       └── SessionWarningDialog.vue
│   └── profile/
│       ├── Toggle2faDialog.vue
│       ├── UpdateEmailDialog.vue
│       ├── UpdatePasswordDialog.vue
│       ├── UpdatePhoneDialog.vue
│       ├── UpdateAddressDialog.vue
│       └── UpdateInfoDialog.vue
├── composables/
│   ├── useErrorHandler.js    — parseApiError wrapper, reactive fieldErrors, errorMessage
│   ├── useLoading.js         — subscribes to axios pending-requests counter
│   └── useSession.js         — session monitoring, refresh, logout
├── i18n/
│   ├── en-US/index.js        — full English translations
│   └── fr-FR/index.js        — full French translations (key parity confirmed)
├── layouts/
│   └── MainLayout.vue        — header + left drawer + router-view
├── pages/
│   ├── DashboardPage.vue     — placeholder
│   ├── ProfilePage.vue       — working profile page (sub-components pattern)
│   ├── ErrorNotFound.vue
│   └── auth/
│       ├── LoginPage.vue
│       ├── RegisterPage.vue
│       ├── OtpPage.vue
│       ├── ForgotPasswordPage.vue
│       ├── ResetPasswordPage.vue
│       └── ActivatePage.vue
├── plugins/
│   └── sessionManager.js     — session timing, refresh logic
├── router/
│   ├── index.js              — defineRouter, navigation guards (requiresAuth, requiresGuest)
│   └── routes.js             — current route tree
├── stores/
│   ├── index.js              — pinia setup
│   └── example-store.js
└── utils/
    └── errorHandler.js       — parseApiError, getFieldError, getFieldErrors, getErrorMessage, getHelpCode
```

### Key Versions (from package.json)

| Package | Version |
|---------|---------|
| vue | ^3.5.22 |
| quasar | ^2.16.0 |
| vue-i18n | ^11.0.0 |
| vue-router | ^4.0.0 |
| axios | ^1.2.1 |
| pinia | ^3.0.1 |
| @quasar/app-vite | ^2.1.0 |

### Quasar Plugins Already Loaded

From `quasar.config.js`:
- `Notify` — already loaded in the `plugins` array, meaning `$q.notify()` is available globally

### Already-Verified FOUND/UXST Patterns

| Requirement | Status | Evidence |
|-------------|--------|----------|
| FOUND-01: `<script setup>` exclusively | VERIFIED | All existing .vue files use `<script setup>` |
| FOUND-01: plain JS (no TS) | VERIFIED | No .ts files, jsconfig.json present |
| FOUND-01: max 250 lines | VERIFIED | ProfilePage.vue delegates to dialog sub-components |
| FOUND-02: en-US + fr-FR full parity | VERIFIED | Both i18n files have identical key structure |
| FOUND-05: loading states | VERIFIED | `useLoading.js`, `:loading` on buttons in LoginPage/DashboardPage |
| FOUND-06: api/ folder by domain | VERIFIED | auth.api.js, account.api.js, profile.api.js, session.api.js |
| FOUND-07: ErrorDto handling | VERIFIED | `utils/errorHandler.js`, `composables/useErrorHandler.js` |
| FOUND-08: lazy-rules | VERIFIED | `lazy-rules` on all q-input fields in LoginPage, RegisterPage |
| UXST-03: sidebar desktop / drawer mobile | VERIFIED | MainLayout.vue uses q-drawer with `show-if-above` |

### Patterns NOT YET Present (gaps to fill)

| Requirement | Gap | What to Build |
|-------------|-----|---------------|
| FOUND-01: primary color `#1976d2` | quasar.config.js has no `brand` config; default Quasar primary IS `#1976d2` so no change needed — but worth documenting | No change needed |
| FOUND-03: Long→String | No utility exists for converting Java Long IDs | Create `utils/longToString.js` |
| FOUND-04: server-side pagination | No pagination component exists | Create `components/common/ServerPagination.vue` |
| UXST-01: `$q.notify()` | Notify plugin IS loaded but existing pages use `q-banner` instead of `$q.notify()`. New admin pages must use `$q.notify()` | Document standard pattern; new admin pages follow it |
| Admin routing | No `/admin/**` routes exist | Add admin route group with `requiresAdmin` meta |
| Admin API proxy | `quasar.config.js` proxy has `/api/v1` and `/api` but NOT `/api/admin` specifically | Verify `/api` catch-all covers it — it does (`'/api'` is already proxied) |

### Quasar Primary Color Note

Quasar's default `primary` CSS variable is `#1976d2`. The scaffold sets `bg-primary` in the header and uses `color="primary"` throughout. No explicit brand configuration is needed. Confirmed: `quasar.config.js` `framework.config` is empty `{}`. The default primary matches FOUND-01 exactly.

### Backend Long IDs — Which Fields Are Affected

These backend response fields are `Long` (Java 64-bit) and will lose precision in JavaScript `Number` if not handled:

| Endpoint | Field | Type in Java |
|----------|-------|-------------|
| `GET /api/admin/clients` | `AdminClientDto.id` | `Long` |
| `GET /api/admin/audit/events` | `AuditEventRow.getId()`, `AuditEventRow.getClientId()` | `Long` |
| `GET /api/admin/topups/history` (clientId filter param) | — | `Long` query param |
| `GET /*/api-keys` | `ApiKeyDto.id` | `Long` |

`balance` fields are `long` but represent credit amounts, not IDs — they fit within safe integer range for practical balances and are read-only display values. Still, they should be treated as strings if displayed as identifiers.

JavaScript's `Number.MAX_SAFE_INTEGER` is `2^53 - 1 = 9007199254740991`. PostgreSQL BIGSERIAL IDs can exceed this for high-volume tables. The strategy from FOUND-03 is: display Long IDs as strings, never do arithmetic on them.

### Backend Pagination Shape

The audit log endpoint uses Spring Data's `Page<T>` which serializes to:

```json
{
  "content": [...],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "first": true
}
```

The `ServerPagination` component must work with this shape and emit `page-change` events for parent pages to call the API again.

### Auth / Admin Role

The backend auth guard uses `requiresAuth: true` via cookie check. Admin pages need a stronger guard — `requiresAdmin: true` meta. The current `document.cookie.includes('user=')` check does not verify roles. The navigation guard in `router/index.js` will need extending.

However, the backend already enforces ROLE_ADMIN at the filter chain level for all `/api/admin/**` endpoints — so a 403 will be returned if a non-admin somehow reaches those pages. The frontend guard is an UX guard, not a security boundary.

For the MVP admin UI, the simplest admin check is: call `authApi.checkAuth()` or use a flag in a store. Looking at the existing code, there is no role-aware store. The planner will need to decide: (a) add a Pinia store for user role, or (b) use the backend 403 response as the implicit gate. Option (a) is cleaner for navigation. Option (b) requires no new store but creates a blank page experience.

**Recommendation:** Add a minimal `stores/useAdminStore.js` (or `useUserStore.js`) that tracks `isAdmin` derived from the user cookie or a profile call. Alternatively, prefix admin routes with `/admin/` and check for `requiresAdmin` by attempting the first admin API call on mount — redirect on 403.

---

## Standard Stack

All already installed. No new dependencies needed for Phase 13.

### Core (already present)
| Library | Version | Purpose |
|---------|---------|---------|
| vue | ^3.5.22 | Framework |
| quasar | ^2.16.0 | UI components (QTable, QInnerLoading, QPagination, QBtn, etc.) |
| vue-i18n | ^11.0.0 | i18n, `useI18n()`, `$t()` |
| vue-router | ^4.0.0 | Routing with meta guards |
| axios | ^1.2.1 | HTTP (already configured with interceptors) |
| pinia | ^3.0.1 | State management |

**Installation:** Nothing new to install for Phase 13.

---

## Architecture Patterns

### Existing API Module Pattern

Every domain gets a named export from `api/index.js`:

```javascript
// src/api/admin-clients.api.js
import { api } from 'src/boot/axios';

export const adminClientsApi = {
  getAll() {
    return api.get('/api/admin/clients');
  },
  create(data) {
    return api.post('/api/admin/clients', data);
  }
};
```

Export from `api/index.js`:
```javascript
export { adminClientsApi } from './admin-clients.api';
```

### Loading State Pattern (FOUND-05)

Two approaches are established in the codebase:

**Global loading bar** (automatic, via axios interceptor):
```javascript
// Already works via GlobalLoadingBar.vue + useLoading composable
// No action needed per component
```

**Button-level loading** (for individual operations):
```javascript
// In <script setup>
const isSubmitting = ref(false);

async function handleAction() {
  isSubmitting.value = true;
  try {
    await someApi.doThing();
  } finally {
    isSubmitting.value = false;
  }
}
```
```html
<q-btn :loading="isSubmitting" :disable="isSubmitting" @click="handleAction" />
```

**QInnerLoading** (for content panels):
```html
<q-inner-loading :showing="isLoading">
  <q-spinner-dots size="50px" color="primary" />
</q-inner-loading>
```

### Error Display Pattern (FOUND-07)

Use `useErrorHandler` composable:
```javascript
const { setError, clearError, hasError, errorMessage, helpCode, hasFieldError, getFieldError } = useErrorHandler();
```

Display via `q-banner` (current pattern in existing pages) OR `$q.notify()` (UXST-01 requirement for NEW admin pages):
```javascript
// For success/error notifications in admin pages — use $q.notify()
import { useQuasar } from 'quasar';
const $q = useQuasar();

$q.notify({ type: 'positive', message: t('success.clientCreated') });
$q.notify({ type: 'negative', message: errorMessage.value });
```

### Admin Route Structure

```javascript
// In routes.js — add inside the '/' MainLayout children:
{
  path: 'admin',
  meta: { requiresAuth: true, requiresAdmin: true },
  children: [
    { path: '', redirect: '/admin/clients' },
    { path: 'clients', component: () => import('pages/admin/ClientsPage.vue') },
    { path: 'topups', component: () => import('pages/admin/TopupsPage.vue') },
    { path: 'sms', component: () => import('pages/admin/SmsMonitorPage.vue') },
    { path: 'webhooks', component: () => import('pages/admin/WebhooksPage.vue') },
    { path: 'audit', component: () => import('pages/admin/AuditPage.vue') },
    { path: 'dashboard', component: () => import('pages/admin/AdminDashboardPage.vue') },
  ]
}
```

Navigation guard extension in `router/index.js`:
```javascript
// Add to beforeEach:
const requiresAdmin = to.matched.some(r => r.meta.requiresAdmin);
if (requiresAdmin && isAuthenticated) {
  // Check admin role — simplest approach: let backend 403 redirect
  // OR check a store value set at login
}
```

### Sidebar Admin Navigation (UXST-03)

Add admin links to `MainLayout.vue` drawer. The drawer already exists with `show-if-above` (sidebar on desktop, hidden on mobile with burger menu). Admin links should be conditionally rendered based on `isAdmin`.

### i18n Extension Pattern (FOUND-02)

Add new keys to BOTH language files simultaneously. Structure for admin namespace:

```javascript
// en-US/index.js — add:
admin: {
  title: 'Administration',
  clients: 'Clients',
  topups: 'Top-ups',
  // ...
}
```

```javascript
// fr-FR/index.js — add identical keys:
admin: {
  title: 'Administration',
  clients: 'Clients',
  topups: 'Recharges',
  // ...
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pagination UI | Custom prev/next buttons | `QPagination` (Quasar) | Already in Quasar, accessible, handles edge cases |
| Notification toasts | Custom toast component | `$q.notify()` (Quasar Notify plugin already loaded) | Configured, WCAG compliant, already in quasar.config.js plugins |
| Loading overlay | Custom spinner overlay | `QInnerLoading` | Part of Quasar, works with q-card/q-page |
| BigInt parsing | BigInt API | `String(value)` coercion at API boundary | Simpler, no BigInt arithmetic needed |
| Error parsing | Custom try/catch | `useErrorHandler` composable | Already built and tested |
| API interceptors | Per-component axios setup | Use `src/boot/axios` `api` instance | Already configured with auth, loading, session handling |

---

## Common Pitfalls

### Pitfall 1: Long ID Precision Loss
**What goes wrong:** `JSON.parse('{"id":9007199254740993}')` silently corrupts the ID to `9007199254740992`. This is a native JavaScript number precision limit.
**Why it happens:** Axios uses `JSON.parse` by default. IDs from PostgreSQL BIGSERIAL can exceed `Number.MAX_SAFE_INTEGER` (`2^53 - 1`).
**How to avoid:** Use a custom `transformResponse` in the axios instance OR enforce that all Long IDs are rendered via a utility that converts to String before display. Since the admin UI only displays IDs (never sends them back as numbers), the simplest approach is to treat them as opaque strings throughout.
**Warning signs:** IDs ending in odd digits that display as even numbers.

### Pitfall 2: `requiresAdmin` Guard Without Role Info
**What goes wrong:** The navigation guard checks `document.cookie.includes('user=')` for auth but has no role data — non-admin authenticated users can navigate to admin routes in the browser.
**Why it happens:** The current cookie only stores the username, not roles.
**How to avoid:** Either (a) add a Pinia store that holds role info populated after login, or (b) accept that the backend will 403 non-admin requests. For Phase 13, document this as a known limitation and add a note that a role-aware store should be introduced before Phase 14. The backend is the actual security boundary.

### Pitfall 3: `$q.notify()` Requires Quasar Instance
**What goes wrong:** Calling `$q.notify()` outside of a component setup context fails silently or throws.
**Why it happens:** `useQuasar()` returns the Quasar instance only within `<script setup>` or `setup()`.
**How to avoid:** Always call `const $q = useQuasar()` at the top of `<script setup>`. Never call it inside async functions or outside setup.

### Pitfall 4: Pagination State Leaking Between Navigation
**What goes wrong:** User navigates away and back to a listing page; stale page/filter state remains.
**Why it happens:** Reactive refs retain values unless explicitly reset on mount.
**How to avoid:** Reset pagination state in `onMounted` or use `watch(route, reset)`.

### Pitfall 5: Missing French Translations
**What goes wrong:** New keys added in en-US but forgotten in fr-FR; UI shows key path string instead of translation.
**Why it happens:** Manual process with no compile-time enforcement.
**How to avoid:** Always add keys to BOTH files in the same commit. The planner should create tasks that explicitly name both files.

### Pitfall 6: Proxy Not Covering New Admin Routes
**What goes wrong:** Dev server returns 404/CORS error for admin API calls.
**Why it happens:** New endpoint prefixes not added to quasar.config.js devServer.proxy.
**How to avoid:** Verify the existing `/api` proxy entry covers `/api/admin/**` — it does, because the proxy key `/api` matches any path starting with `/api`. No change needed.

---

## Code Examples

### Long→String Utility

```javascript
// Source: FOUND-03 requirement + JS Number precision behavior
// src/utils/longToString.js

/**
 * Convert a Long value from the backend API to a safe String.
 * Java Long values can exceed JS Number.MAX_SAFE_INTEGER (2^53 - 1).
 * Always call this on ID fields before displaying or passing to components.
 *
 * @param {number|string|null} value
 * @returns {string|null}
 */
export function longToString(value) {
  if (value === null || value === undefined) return null;
  return String(value);
}

/**
 * Convert all Long ID fields in an object to Strings.
 * Use for API response normalization.
 *
 * @param {Object} obj
 * @param {string[]} fields - field names that are Long IDs
 * @returns {Object} new object with specified fields converted to String
 */
export function normalizeLongIds(obj, fields) {
  if (!obj) return obj;
  const result = { ...obj };
  fields.forEach(f => {
    if (result[f] !== undefined && result[f] !== null) {
      result[f] = String(result[f]);
    }
  });
  return result;
}
```

### ServerPagination Component

```html
<!-- src/components/common/ServerPagination.vue -->
<template>
  <div v-if="totalPages > 1" class="row justify-center q-mt-md">
    <q-pagination
      v-model="currentPage"
      :max="totalPages"
      :max-pages="7"
      boundary-numbers
      direction-links
      @update:model-value="onPageChange"
    />
    <div class="text-caption text-grey-7 q-ml-md self-center">
      {{ t('pagination.summary', { from: fromItem, to: toItem, total: totalElements }) }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

const props = defineProps({
  totalElements: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  pageSize: { type: Number, default: 20 },
  modelValue: { type: Number, default: 1 } // 1-based for QPagination
});

const emit = defineEmits(['update:modelValue', 'page-change']);

const currentPage = ref(props.modelValue);

watch(() => props.modelValue, val => { currentPage.value = val; });

const fromItem = computed(() => (currentPage.value - 1) * props.pageSize + 1);
const toItem = computed(() => Math.min(currentPage.value * props.pageSize, props.totalElements));

function onPageChange(page) {
  emit('update:modelValue', page);
  emit('page-change', page - 1); // Convert to 0-based for backend
}
</script>
```

### Admin API Module Example

```javascript
// src/api/admin-clients.api.js
import { api } from 'src/boot/axios';

export const adminClientsApi = {
  getAll() {
    return api.get('/api/admin/clients');
  },
  create(data) {
    return api.post('/api/admin/clients', data);
  }
};
```

### Notify Pattern (UXST-01)

```javascript
// In <script setup>
import { useQuasar } from 'quasar';
import { useI18n } from 'vue-i18n';

const $q = useQuasar();
const { t } = useI18n();

// Success:
$q.notify({ type: 'positive', message: t('success.clientCreated') });

// Error with helpCode:
$q.notify({
  type: 'negative',
  message: errorMessage.value,
  caption: helpCode.value ? `${t('error.helpCode')}: ${helpCode.value}` : undefined,
  timeout: 5000
});
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `q-banner` for errors (existing pages) | `$q.notify()` for admin pages (UXST-01) | Admin pages use notify; existing auth pages keep banners |
| No pagination (existing pages) | QPagination + ServerPagination.vue (admin listing pages) | New reusable component needed |
| No Long handling (no backend IDs exposed yet) | `longToString()` utility (FOUND-03) | New utility needed before any admin page fetches IDs |

---

## Open Questions

1. **Admin role detection in navigation guard**
   - What we know: Backend enforces ROLE_ADMIN on all `/api/admin/**` routes. Frontend has no role store.
   - What's unclear: Should Phase 13 introduce a Pinia user-role store, or defer to Phase 14 when the first admin page is built?
   - Recommendation: Phase 13 adds `requiresAdmin: true` meta to route config and documents that the guard is incomplete. Phase 14 adds the first admin API call, at which point a user store can be wired. This keeps Phase 13 scope tight.

2. **`$q.notify()` vs `q-banner` for error display**
   - What we know: UXST-01 says notifications use `$q.notify()`. Existing auth pages use `q-banner`. Planner must decide if existing pages are in scope for refactoring.
   - What's unclear: Should Phase 13 retrofit existing auth pages?
   - Recommendation: No. Phase 13 establishes the pattern for NEW admin pages only. Existing auth pages are out of scope.

3. **QPagination: 1-based vs 0-based page numbers**
   - What we know: Quasar's QPagination uses 1-based page numbers. Spring Data uses 0-based page numbers in query params.
   - Resolution: The `ServerPagination` component handles the conversion: emits `page - 1` to parent which passes it to the API.

---

## Sources

### Primary (HIGH confidence)
- Codebase direct inspection — `src/frontend/src/` directory, all files read
- `quasar.config.js` — confirmed Notify plugin loaded, proxy configuration, no brand override needed
- `package.json` — confirmed versions
- Backend Java sources — `AdminClientDto.java`, `AuditEventRow.java`, `ApiKeyDto.java` — confirmed Long field types

### Secondary (MEDIUM confidence)
- Quasar QPagination documented behavior (1-based page numbers) — from training knowledge, consistent with Quasar 2.x
- JavaScript `Number.MAX_SAFE_INTEGER` = `2^53 - 1` — ECMAScript specification, well-known

---

## Metadata

**Confidence breakdown:**
- What already exists: HIGH — directly read from source
- What's missing: HIGH — derived from requirement gaps vs actual code
- Long precision issue: HIGH — ECMAScript spec
- Quasar component APIs: MEDIUM — training data, consistent with v2.16 patterns

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (stable stack, no fast-moving dependencies)
