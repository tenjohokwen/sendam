---
phase: 17-dashboard
plan: 03
subsystem: ui
tags: [vue3, quasar, i18n, composables, promise-all, dashboard]

# Dependency graph
requires:
  - phase: 17-01
    provides: admin.dashboard i18n namespace and five adminApi dashboard methods
  - phase: 17-02
    provides: DashboardSmsCard, DashboardBillingCard, DashboardSystemCard pure-display components
provides:
  - AdminDashboardPage.vue — full orchestrator page with six parallel API calls, three card components, QInnerLoading overlay, and error banner
affects:
  - Phase 18 (v1.2 release) — dashboard is the final feature needed to close out v1.2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Promise.all orchestrator pattern: six parallel API calls in a single try/finally block, all assigned destructured from the settled array
    - client-side derived metric pattern: activeClientCount and totalCredits computed from the clients array, not from dedicated endpoints
    - normalizeLongIds scoped to client IDs only — numeric aggregates (stats, health) never stringified

key-files:
  created: []
  modified:
    - src/frontend/src/pages/admin/AdminDashboardPage.vue

key-decisions:
  - "normalizeLongIds applied only to clients array (each client's id field); never applied to numeric stat/health aggregates"
  - "activeClientCount and totalCredits derived client-side from clients.value — no dedicated aggregate endpoint needed"
  - "useErrorHandler used for error display (hasError / errorMessage / setError / clearError) — consistent with ClientsPage and WebhooksPage patterns"

patterns-established:
  - "Dashboard orchestrator pattern: page owns all refs, fires Promise.all on mount, passes data slices as props to pure-display card components"
  - "isLoading + clearError called at top of loadAll(); QInnerLoading :showing='isLoading' inside q-page for correct overlay positioning"

# Metrics
duration: ~5min
completed: 2026-03-13
---

# Phase 17 Plan 03: AdminDashboardPage Orchestrator Summary

**AdminDashboardPage.vue rewritten from 12-line stub into a full orchestrator: six parallel API calls via Promise.all on mount, three card components composed with typed prop slices, QInnerLoading overlay, error banner, and client-side derived metrics (101 lines)**

## Performance

- **Duration:** ~5 min (Task 1) + human verification pass
- **Started:** 2026-03-13T01:00:00Z
- **Completed:** 2026-03-14T00:00:00Z
- **Tasks:** 2 (Task 1 auto + Task 2 checkpoint:human-verify — approved)
- **Files modified:** 1 (by this plan)

## Accomplishments

- Rewrote AdminDashboardPage.vue from a 12-line stub into a working 101-line orchestrator
- Six API calls (getClients, getDeliveryStats, getCreditSummary, getCircuitBreakerHealth, getProviderStats, getWebhookHealth) fire in parallel via Promise.all on mount and on Refresh click
- Three dashboard cards receive correct prop slices: DashboardSmsCard gets smsStats, DashboardBillingCard gets creditSummary + activeClientCount + totalCredits, DashboardSystemCard gets circuitBreaker + providerStats + webhookStats
- activeClientCount and totalCredits derived client-side from the clients array (no extra endpoint)
- normalizeLongIds applied only to client ID fields, not to numeric stat aggregates

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite AdminDashboardPage.vue** - `298c9ec` (feat)
2. **Task 2: checkpoint:human-verify** - approved by user (no code commit; visual verification)

**Plan metadata:** `44dfef6` (docs: create SUMMARY and update STATE at checkpoint)

## Files Created/Modified

- `src/frontend/src/pages/admin/AdminDashboardPage.vue` - Full dashboard orchestrator (101 lines, up from 12-line stub)

## Decisions Made

- `normalizeLongIds` applied only to the clients array (each client `id` field) — the stat/health responses are all numeric aggregates, not Long IDs, so no stringification needed
- `activeClientCount` and `totalCredits` derived client-side from `clients.value` using `.filter(c => c.status === 'ACTIVE').length` and `.reduce((sum, c) => sum + (c.balance ?? 0), 0)` — no dedicated aggregate endpoint required
- Error handling via `useErrorHandler` composable (`hasError` / `errorMessage` / `setError` / `clearError`) — consistent with the existing pattern in ClientsPage.vue and WebhooksPage.vue

## Deviations from Plan

None - plan executed exactly as written.

## Bugs Fixed During Human Verification (Outside This Plan)

Three bugs surfaced during browser verification and were fixed in separate commits (not part of 17-03 scope):

**1. PostgreSQL null type inference in native queries**
- **Found during:** Human verification (backend errors visible in browser)
- **Issue:** Four native-query repositories (DeliveryAnalyticsRepository, TopupRequestRepository, AuditEventRepository, CreditLedgerRepository) failed with PostgreSQL null type inference when optional parameters were null
- **Fix:** Added `CAST(:param AS type) IS NULL` pattern to all four repositories
- **Commits:** `c55dccd`, `2502c09`

**2. Admin menu visibility after login**
- **Found during:** Human verification (sidebar did not show admin links immediately after login)
- **Issue:** `fetchUser()` was only called on navigation guards; sidebar role-check was stale until page refresh
- **Fix:** `fetchUser()` now called in LoginPage.vue and OtpPage.vue success handlers
- **Commit:** `c766706`

**3. Router guard regression (separate fix)**
- **Found during:** Human verification session
- **Issue:** Router guard had been altered; restored to original `requiresAdmin` only behaviour
- **Commit:** included in `c766706`

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 17 (Dashboard) is fully complete — all three plans (17-01, 17-02, 17-03) delivered and human-verified
- AdminDashboardPage.vue is live in the browser with real backend data
- Phase 18 (v1.2 release) can begin immediately
- No blockers

---
*Phase: 17-dashboard*
*Completed: 2026-03-13*
