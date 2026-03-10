---
phase: 03-send-sms
verified: 2026-03-10T19:15:58Z
status: passed
score: 13/13 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 10/13
  gaps_closed:
    - "POST /v1/sms/send with any invalid Cameroon recipient number returns 400 with INVALID_PHONE_NUMBER"
    - "POST /v1/sms/send when recipients exceed 1000/min rate limit returns 429"
    - "Cancel on an already-submitted, non-scheduled, or non-existent request returns 409 CANCEL_NOT_ALLOWED"
  gaps_remaining: []
  regressions: []
---

# Phase 3: Send SMS Verification Report

**Phase Goal:** Clients can send single, bulk, and scheduled SMS messages with atomic credit reservation, all-or-nothing validation, and idempotency.
**Verified:** 2026-03-10T19:15:58Z
**Status:** passed
**Re-verification:** Yes — after gap closure (plan 03-04)

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                                               | Status      | Evidence                                                                       |
|-----|---------------------------------------------------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------|
| 1   | Flyway V5 creates send_request and send_request_recipient with UNIQUE(client_id, send_request_id)                  | VERIFIED    | V5__send_request.sql line 20: CONSTRAINT uq_send_request_client_ref UNIQUE     |
| 2   | SendRequest JPA entity maps send_status column without Hibernate collision                                          | VERIFIED    | @Column(name="send_status") on sendStatus; no duplicate status field           |
| 3   | SmsSegmentCalculator returns correct segment counts for GSM-7 and UCS-2                                             | VERIFIED    | GSM-7: 160/153 thresholds; UCS-2: 70/67 thresholds — logic correct            |
| 4   | LockTimeoutException and CannotAcquireLockException handlers → 503                                                  | VERIFIED    | Both handlers in ApiAdvice lines 446 and 460                                   |
| 5   | ClientSecurityConfiguration securityMatcher covers /v1/**                                                           | VERIFIED    | AppEndpoints.CLIENT_API = "/v1/**"; used in securityMatcher                    |
| 6   | POST /v1/sms/send valid → 200 ACCEPTED with reserved_credits = segmentCount × recipientCount                       | VERIFIED    | Full 12-step orchestration in SmsService.sendSms; toResponse wired correctly   |
| 7   | POST /v1/sms/send invalid recipient → 400 INVALID_PHONE_NUMBER listing all invalid numbers                         | VERIFIED    | SmsService throws SmsValidationException; ApiAdvice @ExceptionHandler(SmsValidationException.class) → @ResponseStatus(HttpStatus.BAD_REQUEST) at line 401 |
| 8   | POST /v1/sms/send insufficient balance → 400 INSUFFICIENT_CLIENT_BALANCE                                           | VERIFIED    | InsufficientBalanceException has its own 400 handler in ApiAdvice (no regression) |
| 9   | POST /v1/sms/send duplicate sendRequestId → 200 original response (idempotency)                                     | VERIFIED    | Step 1 in sendSms returns early with existing record; still present at line 71  |
| 10  | POST /v1/sms/send over 1000 recipients/min → 429                                                                   | VERIFIED    | SmsService throws RateLimitExceededException; ApiAdvice @ExceptionHandler(RateLimitExceededException.class) → @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) at line 431 |
| 11  | GET /v1/sms/status/{sendRequestId} returns per-recipient status rows                                               | VERIFIED    | getStatus queries recipients, paginates, returns MessageStatusResponse (no regression) |
| 12  | All validation steps happen before credit reservation                                                               | VERIFIED    | Steps 1-7 (idempotency, rate limit, sender, message, recipients, schedule) before step 8 (reserve) — no reordering detected |
| 13  | DELETE /v1/sms/scheduled/{id} on ACCEPTED scheduled → 200 CANCELLED, credits released                             | VERIFIED    | cancelScheduled guards ACCEPTED+scheduleTime, calls release(), transitions to CANCELLED (no regression) |
| 14  | Cancel on non-ACCEPTED or non-scheduled request → 409 CANCEL_NOT_ALLOWED                                           | VERIFIED    | SmsService throws CancelNotAllowedException; ApiAdvice @ExceptionHandler(CancelNotAllowedException.class) → @ResponseStatus(HttpStatus.CONFLICT) at line 416 |
| 15  | Scheduler poller transitions due ACCEPTED requests to SUBMITTED every 30s                                           | VERIFIED    | @Scheduled(fixedDelay=30_000) in SmsSchedulerService, findDueScheduledRequests wired (no regression) |

**Score:** 13/13 truths verified (all gaps from plan 03-04 closed; no regressions)

---

## Gap Closure Verification

### Gap 1 (previously truth 7): INVALID_PHONE_NUMBER → HTTP 400

**Previous state:** SmsService threw bare `ApplicationException(SmsError.INVALID_PHONE_NUMBER)`. No handler in ApiAdvice → fell to defaultErrorHandler → HTTP 500.

**Current state:** SmsService throws `SmsValidationException` at lines 96, 104, 112, 119. `ApiAdvice` has `@ExceptionHandler(SmsValidationException.class)` returning `@ResponseStatus(HttpStatus.BAD_REQUEST)` at line 401. Error code is extracted from `exception.getErrorCode().getErrorCode()` and included in the response.

**Verification:** No bare `ApplicationException` throws remain in SmsService for SMS validation scenarios (grep confirms 0 matches). SmsValidationException extends ApplicationException, carries SmsError.

### Gap 2 (previously truth 10): Recipient rate limit → HTTP 429

**Previous state:** SmsService threw `AuthorizationException(SecurityError.TOO_MANY_REQUESTS)`. ApiAdvice `handleAuthException` always returns 401 regardless of error code.

**Current state:** SmsService throws `RateLimitExceededException` at line 90. `ApiAdvice` has `@ExceptionHandler(RateLimitExceededException.class)` returning `@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)` at line 431. Class extends `ApplicationException` directly (not `AuthorizationException`), so the existing 401 path is not affected.

**Verification:** No `AuthorizationException` throws remain in SmsService (grep confirms 0 matches). The 401 path for auth failures is unchanged.

### Gap 3 (previously truth 14): CANCEL_NOT_ALLOWED → HTTP 409

**Previous state:** `cancelScheduled` threw bare `ApplicationException(SmsError.CANCEL_NOT_ALLOWED)`. No handler in ApiAdvice → HTTP 500.

**Current state:** `cancelScheduled` throws `CancelNotAllowedException` at line 236. ApiAdvice has `@ExceptionHandler(CancelNotAllowedException.class)` returning `@ResponseStatus(HttpStatus.CONFLICT)` at line 416. Constructor hard-wires `SmsError.CANCEL_NOT_ALLOWED`.

**Verification:** No bare `ApplicationException` throws remain in SmsService (confirmed above).

---

## Required Artifacts (Regression Check)

| Artifact                                                                                                           | Status   | Note                                                        |
|--------------------------------------------------------------------------------------------------------------------|----------|-------------------------------------------------------------|
| `src/main/java/com/softropic/sendam/client/contract/exception/SmsValidationException.java`                        | NEW + VERIFIED | 14 lines, extends ApplicationException, correct constructor |
| `src/main/java/com/softropic/sendam/client/contract/exception/CancelNotAllowedException.java`                     | NEW + VERIFIED | 14 lines, extends ApplicationException, hard-wires CANCEL_NOT_ALLOWED |
| `src/main/java/com/softropic/sendam/client/contract/exception/RateLimitExceededException.java`                    | NEW + VERIFIED | 19 lines, extends ApplicationException (not AuthorizationException) |
| `src/main/java/com/softropic/sendam/client/service/SmsService.java`                                               | VERIFIED | 277 lines — all 6 throw sites updated to typed exceptions; key wiring intact |
| `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`                                                  | VERIFIED | Three new handlers at lines 400-435; all returning correct HTTP status codes |
| All previously-verified artifacts (SmsResource, SmsSchedulerService, schema, entities, etc.)                       | NO REGRESSION | Line counts and key patterns stable |

---

## Key Link Verification (Full)

| From                      | To                                          | Via                                          | Status    | Details                                                                  |
|---------------------------|---------------------------------------------|----------------------------------------------|-----------|--------------------------------------------------------------------------|
| SendRequest.java          | SendRequestStatus.java                      | @Enumerated(EnumType.STRING) on sendStatus   | WIRED     | @Enumerated @Column(name="send_status") |
| ClientSecurityConfiguration | /v1/**                                    | securityMatcher(AppEndpoints.CLIENT_API)     | WIRED     | AppEndpoints.CLIENT_API = "/v1/**" |
| SmsResource.java          | SmsService.java                             | @RequiredArgsConstructor injection           | WIRED     | sendSms, getStatus, cancelScheduled all delegated |
| SmsService.java           | CreditReservationService.java               | reserve() at line 131                        | WIRED     | creditReservationService.reserve(clientId, totalCredits, reference) |
| SmsService.java           | CreditReservationService.java               | release() at line 242                        | WIRED     | creditReservationService.release(clientId, request.getReservationId()) |
| SmsService.java           | SmsSegmentCalculator.java                   | static call at line 125                      | WIRED     | SmsSegmentCalculator.calculate(request.message()) |
| SmsService.java           | CamMobileValidator.java                     | static call at line 273                      | WIRED     | CamMobileValidator.validate(phone) |
| SmsSchedulerService.java  | SendRequestRepository.java                  | findDueScheduledRequests at line 40          | WIRED     | sendRequestRepository.findDueScheduledRequests(now) |
| SmsService.java           | SmsValidationException                      | throw at lines 96, 104, 112, 119             | WIRED     | All SMS validation failures use typed exception |
| SmsService.java           | CancelNotAllowedException                   | throw at line 236                            | WIRED     | Cancel guard uses typed exception |
| SmsService.java           | RateLimitExceededException                  | throw at line 90                             | WIRED     | Rate limit check uses typed exception |
| ApiAdvice.java            | SmsValidationException                      | @ExceptionHandler at line 400                | WIRED     | → HTTP 400 BAD_REQUEST |
| ApiAdvice.java            | CancelNotAllowedException                   | @ExceptionHandler at line 415                | WIRED     | → HTTP 409 CONFLICT |
| ApiAdvice.java            | RateLimitExceededException                  | @ExceptionHandler at line 430                | WIRED     | → HTTP 429 TOO_MANY_REQUESTS |

---

## Anti-Patterns (Remaining)

| File            | Line | Pattern                            | Severity | Impact                                                                                  |
|-----------------|------|------------------------------------|----------|-----------------------------------------------------------------------------------------|
| SmsService.java | 104  | Wrong error code for blank message | Warning  | `throw new SmsValidationException("Message must not be blank", SmsError.INVALID_SENDER_ID)` — uses INVALID_SENDER_ID code for a message validation failure. HTTP status is now correct (400) but error_code in response will say INVALID_SENDER_ID instead of a message-specific code. Non-blocking for phase goal. |
| SmsService.java | 67   | Duplicate @RateLimited             | Warning  | @RateLimited present on both SmsResource.sendSms and SmsService.sendSms — aspect fires twice per request. Functional but wastes rate-limit tokens. Non-blocking. |

No blocker anti-patterns remain. Both pre-existing warnings are out of scope for Phase 3 gap closure.

---

## Human Verification Required

None — all behavioral properties are verifiable from exception class hierarchy, handler annotations, and throw sites in the source code.

---

_Verified: 2026-03-10T19:15:58Z_
_Verifier: Claude (gsd-verifier)_
