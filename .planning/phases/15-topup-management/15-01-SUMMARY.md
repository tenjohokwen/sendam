---
phase: 15-topup-management
plan: 01
subsystem: ui
tags: [vue3, quasar, i18n, admin-api, axios]

# Dependency graph
requires:
  - phase: 14-client-apikey-management
    provides: adminApi module pattern (FOUND-06) and i18n locale structure to extend
provides:
  - adminApi.getTopupHistory(params) — GET /api/admin/topups/history with optional query params
  - adminApi.approveTopup(topupId) — PUT /api/admin/topups/{top_XXX}/approve
  - adminApi.rejectTopup(topupId) — PUT /api/admin/topups/{top_XXX}/reject
  - admin.topups i18n section in en-US (21 keys)
  - admin.topups i18n section in fr-FR (21 keys)
affects:
  - 15-02 TopupsPage implementation (direct dependency on all three API methods and all i18n keys)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Extending adminApi with domain-specific methods following FOUND-06 centralized-api pattern"
    - "i18n parity pattern: add block to both locale files in same commit to guarantee key parity"

key-files:
  created: []
  modified:
    - src/frontend/src/api/admin/index.js
    - src/frontend/src/i18n/en-US/index.js
    - src/frontend/src/i18n/fr-FR/index.js

key-decisions:
  - "getTopupHistory accepts params={} default — all filters (topupStatus, clientId, from, to) are optional"
  - "topupId parameter for approveTopup/rejectTopup must be top_XXX format; raw numeric id returns 404 per backend parseTopupId()"
  - "topupAlreadyProcessed i18n key added for 409 TOPUP_ALREADY_PROCESSED backend error — prevents raw English message leaking through useErrorHandler fallback"

patterns-established:
  - "top_XXX format guard: approveTopup/rejectTopup comments document the format requirement inline"

# Metrics
duration: 1min
completed: 2026-03-12
---

# Phase 15 Plan 01: Top-up Management API + i18n Summary

**Three adminApi topup methods (getTopupHistory/approveTopup/rejectTopup) and 21-key admin.topups i18n section added in en-US and fr-FR, giving Plan 02 (TopupsPage) a complete, independently verifiable foundation**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-03-12T18:44:59Z
- **Completed:** 2026-03-12T18:45:48Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Extended adminApi with getTopupHistory (filterable), approveTopup, and rejectTopup — all targeting the correct `/api/admin/topups/*` endpoints
- Added complete admin.topups section (21 keys) to en-US including table column labels, status values, action labels, toast notifications, empty-state messages, and TOPUP_ALREADY_PROCESSED error key
- Added matching admin.topups section (21 keys) to fr-FR with no diacritics per established project convention; full parity with en-US

## Task Commits

Each task was committed atomically:

1. **Task 1: Add topup API methods to adminApi** - `fd6cde7` (feat)
2. **Task 2: Add admin.topups i18n keys to both locales** - `3d24278` (feat)

**Plan metadata:** (docs commit to follow)

## Files Created/Modified

- `src/frontend/src/api/admin/index.js` - Added getTopupHistory, approveTopup, rejectTopup to adminApi (8 total methods)
- `src/frontend/src/i18n/en-US/index.js` - Added admin.topups block with 21 English keys
- `src/frontend/src/i18n/fr-FR/index.js` - Added admin.topups block with 21 French keys (no diacritics)

## Decisions Made

- `getTopupHistory(params = {})` default-empty params so callers can omit the argument entirely when fetching all records
- Inline comment on `approveTopup` documents the `top_XXX` format requirement to prevent the Pitfall 1 error identified in RESEARCH.md (raw numeric id returns 404)
- `topupAlreadyProcessed` i18n key added explicitly — without it, the 409 TOPUP_ALREADY_PROCESSED error code falls back to the raw backend message which is English-only

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 02 (TopupsPage implementation) can proceed immediately
- All three API methods available on adminApi
- All i18n keys available in both locales — t('admin.topups.*') will resolve without fallback
- Key constraint for Plan 02: `item.id` from history response is a raw Long; Plan 02 must apply `longToString()` and construct `"top_" + item.id` for approve/reject calls (documented in RESEARCH.md Pitfall 1 and 2)

---
*Phase: 15-topup-management*
*Completed: 2026-03-12*
