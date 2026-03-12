---
phase: 14-client-apikey-management
plan: 03
subsystem: ui
tags: [vue3, quasar, admin, api-keys, dialog, clipboard, v-if, longToString]

# Dependency graph
requires:
  - phase: 14-01
    provides: adminApi.getApiKeys/createApiKey/revokeApiKey, useErrorHandler, longToString/normalizeLongIds, admin.apiKeys i18n keys
  - phase: 14-02
    provides: ClientsPage.vue with selectedClient/showApiKeysDialog stubs, components/admin/ directory

provides:
  - ApiKeysDialog.vue: full API key management dialog — list, generate (with raw key reveal), revoke with per-row loading
  - RawKeyDialog.vue: show-once raw key display with navigator.clipboard copy and persistent dialog
  - ClientsPage.vue: ApiKeysDialog wired in via v-if with clientId/clientName props; Manage Keys button fully functional

affects:
  - Phase 15+: pattern for show-once sensitive credential display (v-if unmount + null-out pattern)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - show-once credential pattern: v-if on child dialog + null rawKeyResult after close — raw key dropped from memory (AKEY-04)
    - per-row loading map: isRevoking = ref({}) keyed by string keyId — each row independently loading/disabled
    - v-if dialog unmount on parent close: ApiKeysDialog uses v-if in ClientsPage so all key state is released when dialog closes

key-files:
  created:
    - src/frontend/src/components/admin/ApiKeysDialog.vue
    - src/frontend/src/components/admin/RawKeyDialog.vue
  modified:
    - src/frontend/src/pages/admin/ClientsPage.vue

key-decisions:
  - "v-if (not v-show) on RawKeyDialog inside ApiKeysDialog — component unmounts on close so rawKey string is garbage collectable"
  - "v-if (not v-show) on ApiKeysDialog in ClientsPage — closes cleanly, selectedClient cleared on close to prevent stale data"
  - "isRevoking map pattern: ref({}) keyed by keyId string — independent per-row loading state without shared boolean"
  - "onRawKeyDialogClose nulls rawKeyResult.value immediately after dialog emits false — raw key no longer reachable from component state"

patterns-established:
  - "show-once credential display: v-if + null-out parent ref after child dialog close — secure in-memory disposal"
  - "per-row async loading: isRevoking[key.id] boolean map in ref({}) — avoids shared loading flag that would disable all rows"

# Metrics
duration: ~10min
completed: 2026-03-12
---

# Phase 14 Plan 03: ApiKeysDialog and RawKeyDialog Summary

**Full API key management UI: generate new keys (raw key shown once via v-if clipboard dialog), revoke active keys with per-row loading state, wired into ClientsPage.vue**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-12T14:08:00Z
- **Completed:** 2026-03-12T14:18:58Z
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- ApiKeysDialog.vue (207 lines): loads keys on dialog open via watch, generate + revoke with independent async actions, per-row isRevoking map, error banner, RawKeyDialog mounted v-if inside dialog so raw key unmounts on close
- RawKeyDialog.vue (69 lines): persistent dialog (cannot be accidentally dismissed), raw key in monospace code block, navigator.clipboard copy button with positive/warning notify fallback
- ClientsPage.vue: ApiKeysDialog imported and mounted v-if, receives clientId/clientName from selectedClient; selectedClient cleared on dialog close; TODO comment removed from openManageKeys

## Task Commits

Each task was committed atomically:

1. **Task 1: Build ApiKeysDialog.vue and RawKeyDialog.vue** - `0980b2e` (feat)
2. **Task 2: Wire ApiKeysDialog into ClientsPage.vue** - `5296f34` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/frontend/src/components/admin/ApiKeysDialog.vue` - New; API key list dialog with generate (opens RawKeyDialog v-if), revoke per-row, error handling
- `src/frontend/src/components/admin/RawKeyDialog.vue` - New; show-once raw key display, persistent, navigator.clipboard copy button
- `src/frontend/src/pages/admin/ClientsPage.vue` - ApiKeysDialog import + v-if mount, selectedClient cleared on close, TODO comment removed

## Decisions Made

- **v-if on RawKeyDialog:** The plan requires AKEY-04 (raw key shown once, not retainable). Using v-if ensures the component unmounts and rawKey prop becomes unreachable. Combined with nulling rawKeyResult.value in onRawKeyDialogClose, the raw key string is fully released.
- **v-if on ApiKeysDialog in ClientsPage:** Consistent with plan requirement — dialog unmounts on close so all key state (keys list, isRevoking map, newKeyLabel) is dropped.
- **isRevoking as ref({}) map:** Each row's revoke button checks `!!isRevoking[key.id]` independently — prevents one row's loading state from affecting other rows.
- **No v-show anywhere:** Both dialogs and the parent mount use v-if exclusively as required by the plan.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None — all i18n keys (`admin.apiKeys.*`) were already present in both en-US and fr-FR from Plan 01. adminApi methods (getApiKeys, createApiKey, revokeApiKey) were already implemented from Plan 01. normalizeLongIds and longToString utilities confirmed present. Build and lint passed cleanly on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 14 feature set is now complete: client list (CLNT-01, CLNT-02, CLNT-03), API key management (AKEY-01, AKEY-02, AKEY-03, AKEY-04)
- show-once credential pattern established and ready for reuse in any future plan that reveals sensitive values
- All Phase 14 components lint clean and build passes

---
*Phase: 14-client-apikey-management*
*Completed: 2026-03-12*
