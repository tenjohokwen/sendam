# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-12)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** Phase 16 — SMS Monitoring & Webhooks (v1.2)

## Current Position

Phase: 16 of 18 (SMS Monitoring & Webhooks)
Plan: 4 of 4 (Phase complete — all four plans done)
Status: Phase complete
Last activity: 2026-03-12 — Completed 16-04-PLAN.md (WebhooksPage two-tab monitoring replacing stub; WEBH-01 + WEBH-02)

Progress: v1.0 COMPLETE | v1.1 COMPLETE | v1.2 ██████████ ~97%

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

**14-03 decisions:**
- show-once credential pattern: v-if on RawKeyDialog inside ApiKeysDialog + null rawKeyResult.value in onRawKeyDialogClose — raw key dropped from memory (AKEY-04)
- v-if (not v-show) on ApiKeysDialog in ClientsPage — all key state released on close; selectedClient cleared on close
- per-row loading map: isRevoking = ref({}) keyed by keyId string — independent per-row loading without shared boolean

**15-01 decisions:**
- getTopupHistory accepts params={} default — all filters (topupStatus, clientId, from, to) are optional; callers omit arg for unfiltered results
- topupId for approveTopup/rejectTopup MUST be "top_XXX" format — raw numeric id returns 404; Plan 02 must construct "top_" + item.id after fetch
- topupAlreadyProcessed i18n key added for 409 TOPUP_ALREADY_PROCESSED — prevents raw English backend message leaking through useErrorHandler fallback

**15-02 decisions:**
- topupId constructed as 'top_' + item.id before normalizeLongIds spread — explicit ordering to preserve numeric value for string prefix
- Both Approve and Reject buttons disabled when either isApproving[topupId] or isRejecting[topupId] truthy — prevents double-action per row
- History tab shows item.id (string-normalized) without top_ prefix — prefix only needed for PUT API calls, not display

**16-01 decisions:**
- ADMIN_SMS_MONITOR = /api/admin/sms/monitor/** (not /api/admin/sms/**) — preserves sibling ADMIN_ANALYTICS at /api/admin/sms/analytics/**
- findBySendRequestId(String) derived query added to SendRequestRepository — AdminSmsMonitorService needs String→Long PK resolution for DLR lookup
- WebhookEndpointRow.status uses EntityStatus.name() — WebhookEndpoint has no separate WebhookStatus field, EntityStatus is the lifecycle status

**16-02 decisions:**
- admin.sms has 24 keys and admin.webhooks has 30 keys — plan stated 20/22 but those were undercount of the actual key spec; actual key list is authoritative
- adminApi extension pattern: new methods appended after last existing method with TICKET-ID comment annotations

**16-04 decisions:**
- events column omitted from WebhooksPage Registrations table — removed as prescribed fallback to stay under 250-line limit (234 total)
- deliveryStatus uses binary positive/negative badge; attemptStatus uses named attemptStatusColorMap — distinct semantics kept visually separate

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

Last session: 2026-03-12T20:33:00Z
Stopped at: Completed 16-04-PLAN.md — Phase 16 complete; WebhooksPage two-tab monitoring (WEBH-01 + WEBH-02)
Resume file: None
