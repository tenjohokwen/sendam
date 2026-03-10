---
phase: 04-provider-integration
plan: 03
subsystem: infra
tags: [scheduler, purge, retention, send-request, jpa, jpql, fk-constraint]

# Dependency graph
requires:
  - phase: 04-02
    provides: DrCallbackService with finalized_at population, V6 migration adding finalized_at column, FINALIZED/FAIL_FINALIZED terminal states
  - phase: 03-send-sms
    provides: SmsService.getStatus(), SendRequest/SendRequestRecipient entities, MessageStatusEntry record
provides:
  - SmsPurgeService: daily 02:00 purge of FINALIZED/FAIL_FINALIZED send_request rows older than 30 days (children-before-parents, batched 500)
  - SendRequestRepository.findFinalizedBefore(cutoff): returns List<Long> of IDs with finalized_at < cutoff AND status in FINALIZED/FAIL_FINALIZED
  - SendRequestRepository.deleteAllByIdIn(ids): @Modifying bulk delete of parent rows by ID
  - SendRequestRecipientRepository.deleteBySendRequestIdFkIn(ids): @Modifying bulk delete of child rows by FK
  - 404-after-purge: SmsService.getStatus() already throws ResourceNotFoundException when row not found; purge makes rows disappear, giving 404 automatically
  - MessageStatusEntry confirmed: 5 fields (recipient, state, gatewayMessageId, providerId/provider_message_id, segmentsConsumed) all mapped in getStatus()
affects:
  - Phase 05 (admin API): purge job already handles data lifecycle; no admin endpoint for purge needed

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Child-before-parent delete pattern for FK-constrained bulk deletes (recipients before send_request)
    - Batch-500 processing to avoid unbounded IN-clause lengths on large purge sets
    - JPQL @Modifying queries for bulk deletion (avoids N entity loads for deletes)
    - findFinalizedBefore returns List<Long> (IDs only, not full entities) to minimize memory footprint

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/service/SmsPurgeService.java
  modified:
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRepository.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRecipientRepository.java

key-decisions:
  - "findFinalizedBefore returns List<Long> not List<SendRequest> — IDs sufficient for delete; avoids loading full entity graphs into memory for potentially large result sets"
  - "Batch size 500 for purge — prevents unbounded IN-clause, safe for PostgreSQL parameter count limits; purge is low-priority background job so multiple round-trips are acceptable"
  - "SmsPurgeService does not add @EnableScheduling — already present in ClientConfig; adding it again would be redundant"
  - "Task 2 was no-op verification — MessageStatusEntry and SmsService.getStatus() already had all 5 Phase 4 fields (gateway_message_id, provider_message_id, segments_consumed) wired from Phase 3"

patterns-established:
  - "ID-only bulk delete: SELECT s.id FROM Entity WHERE condition -> List<Long> -> DELETE WHERE id IN :ids (avoids loading entities for deletes)"
  - "FK delete order: always delete child (send_request_recipient) before parent (send_request) in same @Transactional method"

# Metrics
duration: 2min
completed: 2026-03-10
---

# Phase 4 Plan 3: Status Query Verification & 30-Day Purge Job Summary

**SmsPurgeService nightly cron deletes FINALIZED/FAIL_FINALIZED send_request rows (and recipients) older than 30 days in child-before-parent FK-safe batches of 500; getStatus() 404-after-purge falls out of existing ResourceNotFoundException handler**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-10T20:00:29Z
- **Completed:** 2026-03-10T20:02:37Z
- **Tasks:** 2 (1 implementation, 1 no-op verification)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- SmsPurgeService created with `@Scheduled(cron = "0 0 2 * * *")` daily job targeting only FINALIZED/FAIL_FINALIZED records with finalized_at older than 30 days
- FK-safe delete order: recipients (children) deleted before send_request (parents) in the same @Transactional method
- Batched 500 per iteration to avoid unbounded IN-clause lengths on large purge sets
- Two new @Modifying JPQL queries added to repositories; findFinalizedBefore returns List<Long> (IDs only) to minimize memory footprint
- Confirmed MessageStatusEntry already has all 5 fields (gateway_message_id, provider_message_id, segments_consumed) and SmsService.getStatus() correctly maps all 5 from SendRequestRecipient — Task 2 was a no-op verification

## Task Commits

Each task was committed atomically:

1. **Task 1: SmsPurgeService and repository delete queries** - `8adffb8` (feat)
2. **Task 2: Verify MessageStatusEntry Phase 4 fields** - no-op (no files changed)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `client/service/SmsPurgeService.java` - Daily @Scheduled purge of finalized send_request rows + recipients; batch-500, child-before-parent, 30-day cutoff
- `client/repo/SendRequestRepository.java` - Added findFinalizedBefore(@Param cutoff) returning List<Long> and deleteAllByIdIn(@Modifying JPQL bulk delete)
- `client/repo/SendRequestRecipientRepository.java` - Added deleteBySendRequestIdFkIn(@Modifying JPQL bulk delete by FK)

## Decisions Made

- **List<Long> for findFinalizedBefore** — Returning IDs only avoids loading full entity graphs into memory for potentially large purge sets. Sufficient for the subsequent delete queries.
- **Batch size 500** — Prevents unbounded IN-clause lengths; PostgreSQL handles it safely. Purge is a low-priority background job so multiple round-trips are acceptable trade-off.
- **No @EnableScheduling added** — Already declared on ClientConfig; confirmed before implementation.
- **Task 2 no-op** — All 5 MessageStatusEntry fields (recipient, state, gatewayMessageId, providerId/provider_message_id, segmentsConsumed) were pre-wired in Phase 3. SmsService.getStatus() passes all 5 from SendRequestRecipient accessors including getGatewayMessageId(), getProviderMessageId(), getSegmentsConsumed(). No code changes needed.

## Deviations from Plan

None — plan executed exactly as written. Task 2 was correctly anticipated as a potential no-op and handled per plan instruction ("If MessageStatusEntry and SmsService.getStatus() are already correct... this task is a no-op verification").

## Issues Encountered

None — compiled on first attempt. 124 existing tests pass with zero regressions.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 04-provider-integration is COMPLETE (all 3 plans done)
- Phase 05 (admin API): purge lifecycle is handled; no admin purge endpoint needed
- SMS full lifecycle: ACCEPTED -> SUBMITTED -> COMPLETED/FAILED -> FINALIZED/FAIL_FINALIZED -> purged after 30 days
- getStatus() returns 404 after purge via existing ResourceNotFoundException handler — no new exception handling needed

---
*Phase: 04-provider-integration*
*Completed: 2026-03-10*
