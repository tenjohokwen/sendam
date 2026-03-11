---
phase: 07-fix-sender-id-forwarding
plan: 01
subsystem: provider-integration
tags: [nexah, sms, dispatch, sender-id, unit-test, mockito]

# Dependency graph
requires:
  - phase: 04-provider-integration
    provides: NexahDispatchService, NexahClient, NexahSendRequest contract
  - phase: 03-send-sms
    provides: SendRequest entity with sender field populated from client request
provides:
  - NexahDispatchService.dispatch() that forwards the client-specified sender ID to Nexah
  - Unit test confirming senderid field is captured and equals the value from SendRequest.getSender()
affects: [any future phases touching NexahDispatchService or SMS dispatch flow]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ArgumentCaptor assertion pattern for verifying constructor arguments passed to mocked collaborators"

key-files:
  created: []
  modified:
    - src/main/java/com/softropic/sendam/gateway/provider/nexah/service/NexahDispatchService.java
    - src/test/java/com/softropic/sendam/gateway/provider/nexah/service/NexahDispatchServiceTest.java

key-decisions:
  - "nexahProperties.senderid() removed from dispatch() call path — sender ID must come from the persisted SendRequest entity, not global account config"

patterns-established:
  - "Client-supplied fields on SendRequest (sender, message, recipients) are the authoritative source for all Nexah dispatch parameters"

# Metrics
duration: 5min
completed: 2026-03-11
---

# Phase 7 Plan 01: Fix Sender ID Forwarding Summary

**One-line fix in NexahDispatchService replaces nexahProperties.senderid() with request.getSender(), ensuring the client-specified sender ID is forwarded to Nexah on every outbound SMS dispatch**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-11T17:30:00Z
- **Completed:** 2026-03-11T17:35:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Closed WIRING-1 correctness gap from v1.0 milestone audit: every outbound SMS now uses the sender ID the client specified, not the global account sender
- Removed stale `nexahProperties.senderid()` call that was silently overriding client intent
- Added ArgumentCaptor assertion in `dispatch_success()` that directly verifies the senderid field in the NexahSendRequest constructed during dispatch

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix NexahDispatchService — forward request.getSender() to Nexah** - `b20ff24` (fix)
2. **Task 2: Update unit tests — assert forwarded sender ID, remove stale senderid stub** - `b6aa80f` (test)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/gateway/provider/nexah/service/NexahDispatchService.java` - Single line change: nexahProperties.senderid() → request.getSender() in NexahSendRequest constructor
- `src/test/java/com/softropic/sendam/gateway/provider/nexah/service/NexahDispatchServiceTest.java` - Added .sender("MYAPP") to all four builders; removed stale senderid stub; added ArgumentCaptor assertion verifying senderid == "MYAPP"

## Decisions Made

- `nexahProperties.senderid()` is removed from the dispatch call path. The global account sender ID config value was serving as an implicit default that silently overrode client intent. Client-supplied `sender` field on `SendRequest` is the only authoritative source during dispatch.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- WIRING-1 correctness gap closed; all SMS dispatches now forward the correct sender ID
- Full test suite passes: 156 tests, 0 failures, BUILD SUCCESS
- No further changes required to NexahDispatchService for this concern

---
*Phase: 07-fix-sender-id-forwarding*
*Completed: 2026-03-11*
