---
phase: 04-provider-integration
plan: 02
subsystem: infra
tags: [nexah, circuit-breaker, state-machine, delivery-report, credit-reservation, scheduler, resilience4j]

# Dependency graph
requires:
  - phase: 04-01
    provides: NexahClient with sendSms(@CircuitBreaker), NexahDrPayload/NexahDrResponse DTOs, DrCallbackResource stub, ProviderUnavailableException, CircuitBreakerRegistry bean
  - phase: 03-send-sms
    provides: CreditReservationService.debit(), SendRequest/SendRequestRecipient entities, SendRequestStatus enum, SmsSchedulerService stub
provides:
  - NexahDispatchService.dispatch(): submits ACCEPTED requests to Nexah, transitions recipients to SUBMITTED with gateway_message_id + provider_message_id
  - DrCallbackService.processDr(): processes DR callbacks, advances recipient to COMPLETED/FAILED, finalizes parent with debit() when all terminal
  - DrCallbackService.forceFinalizeStaleSms(): force-finalizes stale SUBMITTED requests (24h recovery path)
  - SmsSchedulerService: dispatches both immediate (scheduleTime=null) and scheduled-due requests via NexahDispatchService; hourly stale recovery job
  - SmsService: circuit breaker OPEN/HALF_OPEN check before reserve() — no ledger entry on provider outage
  - Flyway V6: finalized_at column + partial index on send_request
affects:
  - 04-03-PLAN (SmsService NexahClient direct call path — circuit breaker guard already in place)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - DR state machine: SUBMITTED -> COMPLETED/FAILED (per-recipient), all-terminal -> parent FINALIZED/FAIL_FINALIZED
    - Debit cap pattern: Math.min(totalActualSegments, reservedCredits) prevents IllegalArgumentException on over-billing
    - Idempotency via TERMINAL_STATUSES EnumSet: duplicate DRs acknowledged status=1 without re-debiting
    - Per-entry error isolation: DrCallbackService catches per-entry exceptions and returns status=0 for Nexah retry
    - Stale recovery: hourly scheduler calls forceFinalizeStaleSms for requests stuck in SUBMITTED 24+ hours

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/service/NexahDispatchService.java
    - src/main/java/com/softropic/sendam/client/service/DrCallbackService.java
    - src/main/resources/db/migration/V6__send_request_finalized_at.sql
  modified:
    - src/main/java/com/softropic/sendam/client/repo/SendRequest.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRepository.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRecipientRepository.java
    - src/main/java/com/softropic/sendam/client/service/SmsService.java
    - src/main/java/com/softropic/sendam/client/service/SmsSchedulerService.java
    - src/main/java/com/softropic/sendam/client/api/DrCallbackResource.java

key-decisions:
  - "SmsSchedulerService circular dependency resolved by construction order — SmsSchedulerService holds DrCallbackService ref; both are @Service singletons wired by Spring"
  - "NexahDispatchService matches recipients by mobileNo (not by array index) — safer if Nexah reorders or returns partial sms[] results"
  - "forceFinalizeStaleSms uses parent.getSegmentCount() as per-recipient estimated segments (not messageCount * segmentCount) — segmentCount is already the per-message segment count; avoids double-multiplication"
  - "finalizeParentIfAllTerminal is extracted as a shared helper called by both processDr and forceFinalizeStaleSms — single debit() path eliminates duplication"
  - "debitable cast from long to int — reservedCredits is persisted as long but segment counts are int-sized; Math.min result fits int; cast is safe"

patterns-established:
  - "Terminal state set: EnumSet.of(COMPLETED, FAILED, FINALIZED, FAIL_FINALIZED) — checked in both idempotency guard and all-terminal finalization"
  - "Fallback for zero totalActualSegments: use reservedCredits (conservative) to avoid debit(0) IllegalArgumentException"
  - "DR acknowledgement: status=1 for processed/idempotent, status=0 for retry (unknown messageId or unexpected exception)"

# Metrics
duration: 6min
completed: 2026-03-10
---

# Phase 4 Plan 2: DR Callback & State Machine Wiring Summary

**Full SMS state machine wired: ACCEPTED -> SUBMITTED (NexahDispatchService), SUBMITTED -> COMPLETED/FAILED (DrCallbackService DR callbacks), parent FINALIZED/FAIL_FINALIZED with exact debit() billing settlement and 24-hour stale recovery**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-10T19:51:13Z
- **Completed:** 2026-03-10T19:57:52Z
- **Tasks:** 2
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments

- NexahDispatchService submits ACCEPTED requests to Nexah, matches recipients by mobileNo for safe partial-result handling, transitions parent to SUBMITTED
- DrCallbackService processes DR payloads: DELIVRD -> COMPLETED, anything else -> FAILED; all-terminal recipients trigger parent finalization + debit() capped to Math.min(actualSegments, reservedCredits)
- Duplicate DR idempotency (already-terminal recipient returns status=1 without re-debiting); per-entry error isolation (status=0 on exception so Nexah retries that entry)
- SmsService checks CircuitBreaker OPEN or HALF_OPEN before reserve() — no ledger entry created during provider outage
- Hourly stale-SUBMITTED recovery job force-finalizes requests stuck 24+ hours

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway V6, SendRequest.finalizedAt, NexahDispatchService, SmsSchedulerService replacement** - `0f20c80` (feat)
2. **Task 2: DrCallbackService, DrCallbackResource wiring, stale-SUBMITTED recovery** - `aaf4ff6` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `client/service/NexahDispatchService.java` - Dispatches ACCEPTED request to Nexah, persists gateway_message_id + provider_message_id on recipients, transitions to SUBMITTED; matches by mobileNo
- `client/service/DrCallbackService.java` - Processes NexahDrPayload: advances recipient state machine, finalizes parent with debit() when all terminal, forceFinalizeStaleSms() for 24h recovery
- `db/migration/V6__send_request_finalized_at.sql` - Adds finalized_at TIMESTAMP column + partial index on FINALIZED/FAIL_FINALIZED rows
- `client/repo/SendRequest.java` - Added finalizedAt Instant field (@Column finalized_at)
- `client/repo/SendRequestRepository.java` - Added findPendingImmediateRequests() and findStaleSubmittedRequests(cutoff)
- `client/repo/SendRequestRecipientRepository.java` - Added findByGatewayMessageId(String)
- `client/service/SmsService.java` - Added CircuitBreakerRegistry; checks OPEN || HALF_OPEN before reserve()
- `client/service/SmsSchedulerService.java` - Replaces Phase 3 stub: calls nexahDispatchService.dispatch() for both immediate and scheduled-due; added recoverStaleSms() hourly job
- `client/api/DrCallbackResource.java` - Replaces stub body with drCallbackService.processDr(payload); @RequiredArgsConstructor

## Decisions Made

- **Recipient matching by mobileNo, not array index** — Nexah may reorder or return partial results; iterating response by mobileNo and looking up the recipient in a Map<mobileNo, entry> is safe regardless of ordering. Unmatched recipients stay ACCEPTED for stale recovery.
- **`finalizeParentIfAllTerminal` extracted as shared helper** — called by both `processDr` and `forceFinalizeStaleSms` to ensure a single debit() code path. Eliminates duplication and guarantees identical finalization behavior.
- **`debitable` as `int`** — `reservedCredits` is stored as `long` but segment counts are bounded by message size and recipient count, fitting comfortably in `int`. Math.min result cast is safe.
- **Zero segmentsConsumed fallback uses reservedCredits** — if somehow all segments are null/0 (edge case that should never occur), falling back to reservedCredits avoids a debit(0) IllegalArgumentException and charges the full reservation conservatively.
- **`forceFinalizeStaleSms` uses `parent.getSegmentCount()`** — this is the per-message segment count, not total credits. Conservative estimate for each recipient forced to FAILED.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None — all files compiled on first attempt. 124 existing tests pass with zero regressions. The `emailService` circuit breaker errors in test output are intentional simulation in pre-existing email resilience tests.

## User Setup Required

None — no new external service configuration required. Existing `NEXAH_USER`, `NEXAH_PASSWORD`, `NEXAH_SENDERID` env vars from Plan 04-01 cover all Nexah calls.

## Next Phase Readiness

- Plan 04-03 (SmsService direct Nexah integration): `CircuitBreakerRegistry` already injected in SmsService; circuit breaker guard in place; NexahClient.sendSms() ready
- V6 migration (finalized_at) in place for Plan 04-03 purge logic
- No blockers for Plan 04-03

---
*Phase: 04-provider-integration*
*Completed: 2026-03-10*
