---
phase: 13-foundation-extension
plan: 03
subsystem: ui
tags: [vue, quasar, vue-router, i18n, admin, lazy-imports, navigation-guard]

requires:
  - phase: 13-01
    provides: admin/pagination i18n namespaces (admin.nav.*, admin.title) used by stubs and nav links

provides:
  - Admin route group under /admin with 6 lazy-loaded children and requiresAdmin meta
  - requiresAdmin navigation guard in router/index.js (unauthenticated users redirected to /login)
  - 6 admin page stubs (ClientsPage, TopupsPage, SmsMonitorPage, WebhooksPage, AuditPage, AdminDashboardPage)
  - Admin navigation section in MainLayout.vue sidebar (6 links, separator, section header)

affects:
  - Phase 14 (builds real content into these stubs; adds role-based v-if="isAdmin" to nav)
  - Phase 15-17 (each phase fills one or more admin stubs with real tables and forms)

tech-stack:
  added: []
  patterns:
    - "Admin routes use lazy () => import() — admin pages not bundled in initial chunk"
    - "requiresAdmin guard on parent route; children inherit via to.matched.some()"
    - "Phase-deferred role check: security boundary is backend 403; frontend role guard added in Phase 14"
    - "Admin nav section lives inside v-if='isAuthenticated' drawer; v-if='isAdmin' deferred to Phase 14"

key-files:
  created:
    - src/frontend/src/pages/admin/ClientsPage.vue
    - src/frontend/src/pages/admin/TopupsPage.vue
    - src/frontend/src/pages/admin/SmsMonitorPage.vue
    - src/frontend/src/pages/admin/WebhooksPage.vue
    - src/frontend/src/pages/admin/AuditPage.vue
    - src/frontend/src/pages/admin/AdminDashboardPage.vue
  modified:
    - src/frontend/src/router/routes.js
    - src/frontend/src/router/index.js
    - src/frontend/src/layouts/MainLayout.vue

key-decisions:
  - "requiresAdmin guard is redundant with requiresAuth for unauthenticated users (admin parent also sets requiresAuth: true) but kept explicitly per plan for clarity and future role-check placement"
  - "v-if='isAdmin' NOT added to nav section — no role store exists yet. Backend 403 is the real security boundary. Phase 14 will add role-based conditional rendering when user store is introduced."
  - "All 6 page stubs use static 'Loading...' string (not t('common.loading')) per plan spec — real content will replace the placeholder entirely in Phases 14-17"

patterns-established:
  - "Admin route group pattern: parent route sets requiresAuth + requiresAdmin meta; children use lazy imports"
  - "Phase-deferred role guard: comment TODO Phase 14 marks where role check will be added once user store exists"
  - "Admin page stub pattern: q-page + text-h5 heading with admin.nav.* key + static Loading... paragraph"

duration: 2min
completed: 2026-03-12
---

# Phase 13 Plan 03: Admin Routes, Stubs, and Navigation Summary

**Admin route group with requiresAdmin guard, 6 lazy-loaded page stubs, and admin sidebar navigation section wired to existing i18n keys**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-03-12T13:27:42Z
- **Completed:** 2026-03-12T13:29:09Z
- **Tasks:** 3
- **Files modified:** 9 (2 modified, 7 created)

## Accomplishments

- Admin route group added to `routes.js` under `/admin` with 6 lazy-loaded children — Phases 14-17 can target these routes immediately
- `requiresAdmin` navigation guard added to `router/index.js` — unauthenticated access to any `/admin/**` path redirects to `/login?redirect=<path>`
- 6 minimal page stubs created under `src/frontend/src/pages/admin/` — each uses `<script setup>`, `useI18n`, the correct `admin.nav.*` heading key, and a static `"Loading..."` placeholder
- Admin sidebar navigation section appended to `MainLayout.vue` drawer — 6 links with matching icons, section header using `admin.title`, separated from user nav by `<q-separator>`

## Task Commits

Each task was committed atomically:

1. **Task 1: Add admin route group and requiresAdmin guard** - `f6e15e7` (feat)
2. **Task 2: Create six admin page stubs** - `4e57473` (feat)
3. **Task 3: Add admin navigation links to MainLayout sidebar** - `7533819` (feat)

**Plan metadata:** (see final commit below)

## Files Created/Modified

- `src/frontend/src/router/routes.js` - Admin route group with 6 lazy-loaded children and `requiresAdmin: true` meta
- `src/frontend/src/router/index.js` - `requiresAdmin` const + guard block redirecting unauthenticated users
- `src/frontend/src/pages/admin/ClientsPage.vue` - Stub: heading `admin.nav.clients`
- `src/frontend/src/pages/admin/TopupsPage.vue` - Stub: heading `admin.nav.topups`
- `src/frontend/src/pages/admin/SmsMonitorPage.vue` - Stub: heading `admin.nav.sms`
- `src/frontend/src/pages/admin/WebhooksPage.vue` - Stub: heading `admin.nav.webhooks`
- `src/frontend/src/pages/admin/AuditPage.vue` - Stub: heading `admin.nav.audit`
- `src/frontend/src/pages/admin/AdminDashboardPage.vue` - Stub: heading `admin.nav.dashboard`
- `src/frontend/src/layouts/MainLayout.vue` - Admin nav section with 6 links, separator, and `admin.title` header

## Decisions Made

**requiresAdmin guard is intentionally redundant for now.** The admin parent route already sets `requiresAuth: true`, so the existing `requiresAuth` guard at the top of `beforeEach` already catches unauthenticated users before reaching the `requiresAdmin` block. The explicit `requiresAdmin` block was kept per plan direction: it marks the precise location where the full `isAdmin` role check will be inserted in Phase 14, and it makes the intent self-documenting without a Pinia store that doesn't exist yet.

**v-if="isAdmin" deferred to Phase 14.** The admin nav section is visible to all authenticated users in this plan. The backend returns 403 for non-admin API calls — that is the real security boundary. Phase 14 will introduce the user store and wire the role-based conditional rendering. A TODO comment in the template records this intent.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None — all files created/modified without blockers.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 6 admin routes exist and render stub content — Phase 14 client management page can replace `ClientsPage.vue` stub content immediately
- Navigation guard is in place — unauthenticated access is handled correctly
- Admin nav links are visible to authenticated users — role-based filtering is the one remaining gap, addressed in Phase 14
- i18n keys (`admin.nav.*`, `admin.title`) were already added in Plan 13-01 — no i18n work needed in Phase 14 for navigation

---
*Phase: 13-foundation-extension*
*Completed: 2026-03-12*
