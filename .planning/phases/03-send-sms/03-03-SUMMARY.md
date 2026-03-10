---
phase: 03-send-sms
plan: "03"
subsystem: api
tags: [spring-scheduling, sms, credit-reservation, cancel, scheduler]

# Dependency graph
requires:
  - phase: 03-01
    provides: SendRequest entity, SendRequestRepository, CreditReservationService.release()
  - phase: 03-02
    provides: SmsService, SmsResource base endpoints, SendRequestStatus.CANCELLED
provides:
  - SmsSchedulerService: 30s fixedDelay poller transitions due ACCEPTED scheduled requests to SUBMITTED
  - ClientConfig with @EnableScheduling
  - SmsService.cancelScheduled(): cancel ACCEPTED scheduled request, release reservation, write SMS_REFUND
  - DELETE /v1/sms/scheduled/{sendRequestId}: cancel endpoint, returns 200 CANCELLED or 409 CANCEL_NOT_ALLOWED
affects: [04-nexah-dispatch, 05-admin-reporting]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - fixedDelay scheduling for non-overlapping SMS dispatch polls
    - Guard-then-release pattern for cancel: status+scheduleTime guard before any credit mutation

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/config/ClientConfig.java
    - src/main/java/com/softropic/sendam/client/service/SmsSchedulerService.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/SmsService.java
    - src/main/java/com/softropic/sendam/client/api/SmsResource.java

key-decisions:
  - "fixedDelay (not fixedRate) on SmsSchedulerService — next run starts 30s after previous completes, preventing overlapping executions"
  - "SmsSchedulerService does NOT inject CreditReservationService — scheduler only marks SUBMITTED; credit debit is Phase 4 responsibility after Nexah confirmation"
  - "Cancel guard: status=ACCEPTED AND scheduleTime IS NOT NULL — prevents cancelling immediate sends and already-submitted requests"
  - "DELETE /v1/sms/scheduled/{id} not rate-limited — per v8 contract"

patterns-established:
  - "Scheduler stub pattern: transition state now, wire actual HTTP call in next phase"
  - "Cancel guard: check both status AND scheduling flag before releasing any credits"

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 3 Plan 03: Scheduled SMS and Cancel Endpoint Summary

**SmsSchedulerService 30s poller + DELETE /v1/sms/scheduled/{id} with credit release via SMS_REFUND, completing all Phase 3 (SMS-01 through SMS-06) requirements**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-10T19:35:00Z
- **Completed:** 2026-03-10T19:43:45Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- SmsSchedulerService polls every 30s (fixedDelay) for due ACCEPTED scheduled requests and marks them SUBMITTED — stub for Phase 4 Nexah dispatch
- ClientConfig provides @EnableScheduling activation in one dedicated class, separate from security config
- SmsService.cancelScheduled() enforces ACCEPTED+scheduled guard, calls creditReservationService.release() (writes SMS_REFUND entry), transitions request to CANCELLED
- DELETE /v1/sms/scheduled/{sendRequestId} endpoint added to SmsResource — returns 200 CANCELLED or 409 CANCEL_NOT_ALLOWED
- All 124 tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: SmsSchedulerService and ClientConfig @EnableScheduling** - `1e6ae20` (feat)
2. **Task 2: SmsService.cancelScheduled and DELETE /v1/sms/scheduled/{id}** - `82adb20` (feat)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/client/config/ClientConfig.java` - @Configuration @EnableScheduling; activates Spring scheduling
- `src/main/java/com/softropic/sendam/client/service/SmsSchedulerService.java` - @Scheduled(fixedDelay=30000) poller; finds due requests via findDueScheduledRequests(); marks SUBMITTED
- `src/main/java/com/softropic/sendam/client/service/SmsService.java` - Added cancelScheduled() method with guard + release + CANCELLED transition
- `src/main/java/com/softropic/sendam/client/api/SmsResource.java` - Added DELETE /v1/sms/scheduled/{sendRequestId} endpoint

## Decisions Made

- fixedDelay on SmsSchedulerService so runs never overlap under load — prevents double-dispatch of same request if a run takes longer than 30s.
- Scheduler does NOT touch CreditReservationService — Phase 3 marks SUBMITTED only; Phase 4 wires Nexah HTTP call and then calls debit() after delivery confirmation.
- Cancel guard requires BOTH status=ACCEPTED AND scheduleTime IS NOT NULL — an immediate send that was accepted cannot be cancelled, only scheduled sends can be.
- Cancel endpoint not rate-limited per v8 contract specification.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- mvnw wrapper script was missing `.mvn/wrapper/maven-wrapper.properties`, requiring use of system `mvn` instead. Same behavior as prior plans in this session (pre-existing condition).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All Phase 3 requirements (SMS-01 through SMS-06) satisfied end-to-end.
- Phase 4 (Nexah dispatch) can replace the SUBMITTED stub in SmsSchedulerService with an actual HTTP call to Nexah's API, then call creditReservationService.debit() after delivery.
- CreditReservationService.release() is verified correct — SMS_REFUND entry written and balance restored on cancel.
- No blockers for Phase 4.

---
*Phase: 03-send-sms*
*Completed: 2026-03-10*
