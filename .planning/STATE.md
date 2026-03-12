# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-12)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 14 — Client & API Key Management (v1.2)

## Current Position

Phase: 14 of 18 (Client & API Key Management)
Plan: 2 of N (ClientsPage + CreateClientDialog complete)
Status: In progress
Last activity: 2026-03-12 — Completed 14-02-PLAN.md (ClientsPage.vue, CreateClientDialog.vue)

Progress: v1.0 COMPLETE | v1.1 COMPLETE | v1.2 ████░░ ~50%

## Accumulated Context

### Decisions

All v1.0 and v1.1 decisions are logged in PROJECT.md Key Decisions table and archived in:
- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.1-ROADMAP.md`

**13-01 decisions:**
- FOUND-06 pattern: all API calls centralized in `api/<domain>/` folders; no direct axios in components/composables
- FOUND-07 pattern: admin pages import `useErrorHandler` exclusively for API error handling; existing reactive implementation (setError/clearError + computed + i18n) preserved over simpler spec
- Long ID guard: always call `longToString()` on backend ID fields before display (Java BIGSERIAL exceeds JS MAX_SAFE_INTEGER)

**13-02 decisions:**
- ServerPagination pattern: `:total-elements :total-pages :page-size v-model @page-change` — component handles 1-to-0-based conversion internally; callers pass 0-based index directly to `?page=` query param
- v-if (not v-show) on ServerPagination outer wrapper — component truly unmounts when totalPages <= 1

**13-03 decisions:**
- Admin route group: parent sets `requiresAuth: true, requiresAdmin: true`; children use lazy imports; `requiresAdmin` guard defers full role check to Phase 14 (no Pinia user store yet; backend 403 is the real security boundary)
- Admin sidebar nav section: no `v-if="isAdmin"` in this plan — visible to all authenticated users until Phase 14 adds the user store and role-based conditional rendering
- Admin page stubs use static `"Loading..."` string (not i18n key) — placeholder is replaced entirely when Phase 14-17 build real content

**14-01 decisions:**
- UserDto.authorities (Set<String>) is the role field; store named `authorities` to match; values are strings like `"ROLE_ADMIN"`
- profileApi import path: `src/api/profile.api` (flat file, no subfolder)
- beforeEach guard made async to support `await userStore.fetchUser()`
- userStore.reset() called on logout in MainLayout — clears isAdmin immediately without page reload
- isLoaded guard pattern: check `userStore.isLoaded` before fetchUser() to avoid duplicate profile API calls per navigation

**14-02 decisions:**
- statusLabelMap pattern: `{ ACTIVE: () => t('...'), INACTIVE: ... }` map with getter functions for enum-to-i18n labels — reactive, avoids string manipulation
- openManageKeys stub: sets showApiKeysDialog=true but no ApiKeysDialog yet — Plan 03 completes the wiring
- No raw key shown after createClient — backend POST /api/admin/clients returns rawApiKey: null; admin must generate key explicitly in API Keys panel

### Pending Todos

(None — clean slate for v1.2)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — fallback default is documented as unsafe
- Non-blocking tech debt from v1.0/v1.1:
  - Wrong error code for blank-message validation (INVALID_SENDER_ID used instead of message-specific code)
  - Duplicate @RateLimited on SmsResource AND SmsService fires AOP aspect twice per request (wastes rate-limit tokens)
  - TODO comments in AppEndpoints.java and SecurityConfiguration.java
  - WEBHOOK_DELETED enum constant unwired — needs trigger point when delete endpoint added
  - ClientCreditConsumptionResponse in analytics.contract (minor cross-module coupling with spend.service)
  - Boot smoke test for /v1/analytics/** endpoints recommended before production deploy

## Session Continuity

Last session: 2026-03-12T14:21:00Z
Stopped at: Completed 14-02-PLAN.md — ClientsPage.vue (full client list page), CreateClientDialog.vue (create client dialog)
Resume file: None
