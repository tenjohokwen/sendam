---
phase: 13-foundation-extension
plan: 02
subsystem: ui
tags: [vue, quasar, pagination, i18n, spring-data]

# Dependency graph
requires:
  - phase: 13-01
    provides: pagination.summary i18n key added to en-US and fr/index.js
provides:
  - Reusable ServerPagination.vue component for all admin listing pages
affects:
  - 13-03 (clients list page uses ServerPagination)
  - 13-04 (top-ups list page uses ServerPagination)
  - 13-05 (SMS monitor page uses ServerPagination)
  - 13-06 (audit log page uses ServerPagination)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Server-side pagination: pass totalPages + totalElements from Page<T> response; component handles 1-based UI to 0-based Spring Data conversion internally"
    - "v-model on pagination: parent binds :model-value + @page-change; component emits both update:modelValue (1-based) and page-change (0-based)"

key-files:
  created:
    - src/frontend/src/components/common/ServerPagination.vue
  modified: []

key-decisions:
  - "Outer div uses v-if (not v-show) — component truly unmounts when totalPages <= 1, avoiding stale computed values"
  - "watch() on modelValue prop (not two-way v-model) keeps internal state in sync when parent resets page after filter change without triggering extra emit"
  - "fromItem/toItem computed from currentPage ref (not modelValue prop directly) so summary stays accurate between emit and parent update"

patterns-established:
  - "ServerPagination pattern: import component, bind :total-elements :total-pages :page-size v-model + @page-change handler"

# Metrics
duration: 1min
completed: 2026-03-12
---

# Phase 13 Plan 02: ServerPagination Summary

**QPagination wrapper with 1-to-0-based page conversion, summary label via i18n, and external modelValue sync for all admin listing pages**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-03-12T13:27:36Z
- **Completed:** 2026-03-12T13:28:03Z
- **Tasks:** 1 of 1
- **Files modified:** 1

## Accomplishments

- Created `ServerPagination.vue` that renders nothing when `totalPages <= 1` (v-if, not v-show)
- Emits `page-change` with 0-based index so callers pass directly to Spring Data `?page=` query param
- Summary label "1–20 of 150" rendered via `pagination.summary` i18n key added in Plan 01

## Task Commits

Each task was committed atomically:

1. **Task 1: Create ServerPagination.vue component** - `2135d32` (feat)

## Files Created/Modified

- `src/frontend/src/components/common/ServerPagination.vue` - Reusable server-side pagination component wrapping QPagination

## Decisions Made

- Used `v-if` (not `v-show`) on the outer wrapper so the component truly unmounts when `totalPages <= 1`, preventing stale computed values from being observed.
- Watched `props.modelValue` to update internal `currentPage` ref rather than deriving display state purely from the prop — ensures `fromItem`/`toItem` computed values stay accurate between the emit cycle and the parent re-render.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ServerPagination.vue` is ready to be imported by any admin listing page
- Usage pattern: `:total-elements="page.totalElements" :total-pages="page.totalPages" v-model="currentPageOneBased" @page-change="loadPage"`
- Plans 13-03 through 13-06 can consume this component directly

---
*Phase: 13-foundation-extension*
*Completed: 2026-03-12*
