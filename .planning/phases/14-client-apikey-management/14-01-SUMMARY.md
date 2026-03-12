---
phase: 14-client-apikey-management
plan: 01
subsystem: ui
tags: [vue3, quasar, pinia, vue-router, i18n, admin]

# Dependency graph
requires:
  - phase: 13-admin-scaffolding
    provides: Admin route stubs, MainLayout admin nav section with TODO comment, requiresAdmin guard skeleton, i18n admin namespace
provides:
  - adminApi object with 5 methods (getClients, createClient, getApiKeys, createApiKey, revokeApiKey)
  - useUserStore Pinia store with authorities state, isAdmin getter, fetchUser/reset actions
  - requiresAdmin router guard with real ROLE_ADMIN role check via user store
  - Sidebar admin nav section conditionally rendered via v-if="userStore.isAdmin"
  - admin.clients and admin.apiKeys i18n namespaces in en-US and fr-FR
affects:
  - 14-02 through 14-N: All subsequent Phase 14 plans; they depend on adminApi, useUserStore, and i18n keys

# Tech tracking
tech-stack:
  added: []
  patterns:
    - adminApi: all admin HTTP calls in src/api/admin/index.js; callers never use axios directly
    - Pinia user store: role-based access using authorities array from UserDto; isLoaded guard avoids duplicate fetches
    - Async router guard: beforeEach async; requiresAdmin fetches user store on first access, caches in Pinia

key-files:
  created:
    - src/frontend/src/stores/user.store.js
  modified:
    - src/frontend/src/api/admin/index.js
    - src/frontend/src/router/index.js
    - src/frontend/src/layouts/MainLayout.vue
    - src/frontend/src/i18n/en-US/index.js
    - src/frontend/src/i18n/fr-FR/index.js

key-decisions:
  - "UserDto.authorities is Set<String> serialized as JSON array; store uses authorities (not roles) to match backend field name"
  - "profileApi import path is src/api/profile.api (not src/api/profile/index.js)"
  - "userStore.reset() called on logout in MainLayout to clear isAdmin state immediately"
  - "beforeEach guard made async to support await userStore.fetchUser()"

patterns-established:
  - "isLoaded guard: check userStore.isLoaded before fetchUser() to avoid duplicate profile API calls per navigation"

# Metrics
duration: 10min
completed: 2026-03-12
---

# Phase 14 Plan 01: Foundation — API, User Store, Router Guard, Sidebar Summary

**Pinia user store with ROLE_ADMIN isAdmin getter, async requiresAdmin router guard fetching profile, sidebar v-if conditional, and full admin.clients/admin.apiKeys i18n coverage in en-US and fr-FR**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-12T13:56:03Z
- **Completed:** 2026-03-12T14:06:00Z
- **Tasks:** 3
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments

- adminApi exported with 5 methods covering all admin client and API key endpoints
- useUserStore created: authorities array state, isAdmin/isLoaded getters, fetchUser/reset actions using profileApi.getProfile()
- requiresAdmin router guard converted to async; real ROLE_ADMIN check via user store; non-admins redirected to /dashboard
- Sidebar admin nav section wrapped in `<template v-if="userStore.isAdmin">` — hidden for non-admin authenticated users
- admin.clients (16 keys) and admin.apiKeys (17 keys) added to both en-US and fr-FR, preserving all existing admin keys

## Task Commits

Each task was committed atomically:

1. **Task 1: Populate api/admin/index.js and create user.store.js** - `021d0e3` (feat)
2. **Task 2: Update router guard and sidebar conditional rendering** - `590cccc` (feat)
3. **Task 3: Add i18n keys for admin.clients and admin.apiKeys** - `73548c3` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/frontend/src/api/admin/index.js` - Replaced placeholder with real adminApi; 5 methods for client/key CRUD
- `src/frontend/src/stores/user.store.js` - New Pinia user store; authorities state, isAdmin getter, fetchUser/reset
- `src/frontend/src/router/index.js` - Async beforeEach guard; requiresAdmin block fetches user store and checks ROLE_ADMIN
- `src/frontend/src/layouts/MainLayout.vue` - useUserStore import, userStore const, admin nav wrapped in v-if="userStore.isAdmin", userStore.reset() on logout
- `src/frontend/src/i18n/en-US/index.js` - admin.clients and admin.apiKeys namespaces added
- `src/frontend/src/i18n/fr-FR/index.js` - admin.clients and admin.apiKeys namespaces added (French translations)

## Decisions Made

- **UserDto field name:** Backend `UserDto.authorities` (Set<String>) maps to store `authorities` array. Values are strings like `"ROLE_ADMIN"`. Confirmed by reading Java source directly. The RESEARCH.md flagged this as LOW confidence; confirmed HIGH.
- **profileApi import path:** File is `src/api/profile.api.js` — import as `src/api/profile.api`. No `index.js` inside a subfolder.
- **userStore.reset() on logout:** Added in MainLayout handleLogout so the sidebar isAdmin binding updates immediately on logout without requiring a page reload.
- **Async guard:** `beforeEach` changed from synchronous `(to, from, next) => {}` to `async (to, from, next) => {}` to support `await userStore.fetchUser()`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added userStore.reset() on logout**

- **Found during:** Task 2 (MainLayout sidebar update)
- **Issue:** Plan specified reset() action in the store but did not explicitly call it on logout. Without it, isAdmin remains true after logout until page reload, meaning the sidebar admin nav would still render briefly.
- **Fix:** Added `userStore.reset()` call in `handleLogout()` in MainLayout.vue, immediately before clearing the username cookie.
- **Files modified:** src/frontend/src/layouts/MainLayout.vue
- **Verification:** logout flow: reset() clears authorities → isAdmin becomes false → v-if removes admin nav immediately
- **Committed in:** `590cccc` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Reset on logout is required for correct reactive behavior. No scope creep.

## Issues Encountered

None — all source files matched what the RESEARCH.md described. The only gap (UserDto.authorities field) was resolved by reading the Java class directly before writing the store.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 5 adminApi methods are wired and ready for Pages to import
- useUserStore is available for all Phase 14 page components
- i18n keys under admin.clients and admin.apiKeys are complete for all subsequent components
- Router will properly block non-admin users from /admin/* routes
- Sidebar admin section is hidden for non-admins (visible for ROLE_ADMIN users after first /admin/* navigation loads the store)

---
*Phase: 14-client-apikey-management*
*Completed: 2026-03-12*
