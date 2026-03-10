---
phase: 03-send-sms
plan: "04"
subsystem: api
tags: [spring-exception-handling, sms, validation, rate-limit, http-status-codes]

# Dependency graph
requires:
  - phase: 03-02
    provides: SmsService with bare ApplicationException and AuthorizationException throw sites
  - phase: 03-03
    provides: SmsService.cancelScheduled() with bare ApplicationException throw site
provides:
  - SmsValidationException: typed ApplicationException subclass for INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME
  - CancelNotAllowedException: typed ApplicationException subclass for CANCEL_NOT_ALLOWED
  - RateLimitExceededException: typed ApplicationException subclass (NOT AuthorizationException) for TOO_MANY_REQUESTS -> HTTP 429
  - ApiAdvice handlers: SmsValidationException -> 400, CancelNotAllowedException -> 409, RateLimitExceededException -> 429
affects: [04-nexah-dispatch, 05-admin-reporting]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Typed exception subclasses per domain error (not bare ApplicationException) for precise HTTP mapping
    - RateLimitExceededException extends ApplicationException directly (not AuthorizationException) to avoid inheriting 401 mapping

key-files:
  created:
    - src/main/java/com/softropic/sendam/client/contract/exception/SmsValidationException.java
    - src/main/java/com/softropic/sendam/client/contract/exception/CancelNotAllowedException.java
    - src/main/java/com/softropic/sendam/client/contract/exception/RateLimitExceededException.java
  modified:
    - src/main/java/com/softropic/sendam/client/service/SmsService.java
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java

key-decisions:
  - "RateLimitExceededException extends ApplicationException (not AuthorizationException) — distinct class hierarchy allows independent 429 handler without touching the existing 401 path for auth failures"
  - "SmsValidationException carries SmsError as constructor parameter — allows INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME to share one exception class while preserving distinct error_code values in responses"
  - "CancelNotAllowedException hardcodes SmsError.CANCEL_NOT_ALLOWED — single-purpose exception, no ambiguity in error code"

patterns-established:
  - "Domain exception pattern: each HTTP status code boundary gets its own typed exception class in contract.exception; ApiAdvice handler is the single mapping point"
  - "Rate limit exception isolation: rate-limit exceptions must NOT extend AuthorizationException to avoid inheriting the 401 handler"

# Metrics
duration: 4min
completed: 2026-03-10
---

# Phase 3 Plan 04: SMS Exception Gap Closure Summary

**Three typed exception classes (SmsValidationException, CancelNotAllowedException, RateLimitExceededException) closing HTTP 400/409/429 status code gaps left by Phase 3 verification**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-10T19:09:00Z
- **Completed:** 2026-03-10T19:13:30Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- SmsValidationException added: typed ApplicationException subclass carrying SmsError, handles INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME — all five validation throw sites in SmsService updated
- CancelNotAllowedException added: hardcodes SmsError.CANCEL_NOT_ALLOWED — cancelScheduled throw site updated
- RateLimitExceededException added: extends ApplicationException directly (not AuthorizationException) carrying SecurityError.TOO_MANY_REQUESTS — recipient rate limit throw site updated, avoiding 401 inheritance
- ApiAdvice updated with three handlers: SmsValidationException -> HTTP 400, CancelNotAllowedException -> HTTP 409, RateLimitExceededException -> HTTP 429
- Existing AuthorizationException -> HTTP 401 path unchanged, all 124 tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: Create typed exception classes and update SmsService throw sites** - `b1fcff8` (feat)
2. **Task 2: Add exception handlers to ApiAdvice for 400, 409, and 429 responses** - `03bc9ab` (feat)

## Files Created/Modified

- `src/main/java/com/softropic/sendam/client/contract/exception/SmsValidationException.java` - Typed exception for SMS input validation failures; constructor takes (String message, SmsError errorCode)
- `src/main/java/com/softropic/sendam/client/contract/exception/CancelNotAllowedException.java` - Typed exception for cancel conflicts; constructor takes (String message), hardcodes CANCEL_NOT_ALLOWED
- `src/main/java/com/softropic/sendam/client/contract/exception/RateLimitExceededException.java` - Typed exception for recipient rate limit; extends ApplicationException (not AuthorizationException); carries SecurityError.TOO_MANY_REQUESTS
- `src/main/java/com/softropic/sendam/client/service/SmsService.java` - Six throw sites replaced: 5 ApplicationException -> SmsValidationException/CancelNotAllowedException, 1 AuthorizationException -> RateLimitExceededException; unused imports removed
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java` - Three new @ExceptionHandler methods added after topupAlreadyProcessedHandler, before lockTimeoutHandler

## Decisions Made

- RateLimitExceededException extends ApplicationException directly (not AuthorizationException) — this was the key design choice. AuthorizationException already maps to 401 in ApiAdvice; inheriting from it would require override logic or handler ordering tricks. A sibling class under ApplicationException is simpler and unambiguous.
- SmsValidationException carries the SmsError code as a constructor parameter rather than hardcoding it — allows reuse across three distinct error codes (INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME) without needing separate exception classes for each.
- `exception.getErrorCode().getErrorCode()` used in smsValidationHandler to extract the string code — preserves the exact SmsError enum name (e.g. "INVALID_PHONE_NUMBER") in the API response error_code field per v8 contract.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All three verification truths from 03-VERIFICATION.md are now closed:
  - Truth 7 (INVALID_PHONE_NUMBER -> 400): SmsValidationException handler returns 400
  - Truth 10 (rate limit -> 429): RateLimitExceededException handler returns 429
  - Truth 14 (CANCEL_NOT_ALLOWED -> 409): CancelNotAllowedException handler returns 409
- Phase 3 (Send SMS) is fully complete — all six requirements (SMS-01 through SMS-06) satisfied with correct HTTP status codes.
- Phase 4 (Nexah dispatch) can proceed without any pending exceptions or status code issues.

---
*Phase: 03-send-sms*
*Completed: 2026-03-10*
