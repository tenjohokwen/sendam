---
phase: 03-send-sms
verified: 2026-03-11T02:51:09Z
status: passed
score: 6/6 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 13/13
  gaps_closed: []
  gaps_remaining: []
  regressions: []
---

# Phase 3: Send SMS Verification Report

**Phase Goal:** Clients can send single, bulk, and scheduled SMS messages with atomic credit reservation, all-or-nothing validation, and idempotency.
**Verified:** 2026-03-11T02:51:09Z
**Status:** passed
**Re-verification:** Yes — independent fresh check against all 6 requirements (SMS-01 through SMS-06). No gaps from previous run. Checking for regressions.

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                          | Status     | Evidence                                                                                                          |
|----|----------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------------------|
| 1  | Client can send SMS to one or more Cameroon recipients in a single request (SMS-01)                            | VERIFIED   | `POST /v1/sms/send` in SmsResource → SmsService.sendSms; `@NotEmpty @Size(max=1000)` on recipients list          |
| 2  | Any invalid recipient or insufficient balance rejects entire request — no partial sends (SMS-02)               | VERIFIED   | All recipients validated before reserve(); SmsValidationException → HTTP 400; InsufficientBalanceException → 400 |
| 3  | Credits reserved atomically at request time; balance reflects reservation immediately (SMS-03)                 | VERIFIED   | CreditReservationService.reserve() uses SELECT FOR UPDATE pessimistic lock; balance written in same transaction   |
| 4  | Client can schedule SMS for future delivery; credits reserved at submission time (SMS-04)                      | VERIFIED   | scheduleTime field accepted in SendSmsRequest; scheduleTime validation in Step 6; reserved_credits set at step 8 |
| 5  | Client can cancel a scheduled SMS before provider submission; reserved credits fully released (SMS-05)         | VERIFIED   | DELETE /v1/sms/scheduled/{id} → cancelScheduled(); guards ACCEPTED+scheduleTime; calls release(); → CANCELLED    |
| 6  | Duplicate sendRequestId returns original response without re-sending or re-charging (SMS-06)                   | VERIFIED   | Step 1 in sendSms: findByClientIdAndSendRequestId; if present, returns toResponse(existing) with no further ops  |

**Score:** 6/6 requirements verified.

---

## Required Artifacts

| Artifact                                                                                     | Exists | Substantive | Wired | Status     | Notes                                                                      |
|----------------------------------------------------------------------------------------------|--------|-------------|-------|------------|----------------------------------------------------------------------------|
| `src/main/java/.../client/api/SmsResource.java`                                              | Yes    | 82 lines    | Yes   | VERIFIED   | POST /v1/sms/send, GET /v1/sms/status/{id}, DELETE /v1/sms/scheduled/{id} |
| `src/main/java/.../client/service/SmsService.java`                                           | Yes    | 290 lines   | Yes   | VERIFIED   | Full 12-step orchestration; no stubs; no empty returns                     |
| `src/main/java/.../client/service/CreditReservationService.java`                             | Yes    | 233 lines   | Yes   | VERIFIED   | reserve(), release(), debit() — all implemented; pessimistic lock wired    |
| `src/main/java/.../client/service/SmsSchedulerService.java`                                  | Yes    | 109 lines   | Yes   | VERIFIED   | @Scheduled(fixedDelay=30_000); dispatches due+immediate; wired to repo     |
| `src/main/java/.../client/service/SmsSegmentCalculator.java`                                 | Yes    | 73 lines    | Yes   | VERIFIED   | GSM-7 and UCS-2 thresholds correct; called at SmsService line 138          |
| `src/main/java/.../client/contract/exception/SmsValidationException.java`                    | Yes    | 14 lines    | Yes   | VERIFIED   | extends ApplicationException; carries SmsError; handler in ApiAdvice       |
| `src/main/java/.../client/contract/exception/CancelNotAllowedException.java`                 | Yes    | 14 lines    | Yes   | VERIFIED   | extends ApplicationException; hard-wires CANCEL_NOT_ALLOWED               |
| `src/main/java/.../client/contract/exception/RateLimitExceededException.java`                | Yes    | 19 lines    | Yes   | VERIFIED   | extends ApplicationException (not AuthorizationException); → 429           |
| `src/main/java/.../client/contract/SendSmsRequest.java`                                      | Yes    | 25 lines    | Yes   | VERIFIED   | sendRequestId, sender, message, recipients, scheduleTime; JSR-303 present  |
| `src/main/java/.../client/contract/SendSmsResponse.java`                                     | Yes    | 13 lines    | Yes   | VERIFIED   | reserved_credits, available_balance_after_reservation, status all present  |
| `src/main/java/.../client/contract/SendRequestStatus.java`                                   | Yes    | 11 lines    | Yes   | VERIFIED   | ACCEPTED, SUBMITTED, COMPLETED, FAILED, FINALIZED, FAIL_FINALIZED, CANCELLED|
| `src/main/java/.../client/repo/SendRequest.java`                                             | Yes    | 75 lines    | Yes   | VERIFIED   | @Enumerated(STRING) on sendStatus; reservationId, scheduleTime, finalizedAt|
| `src/main/java/.../client/repo/SendRequestRepository.java`                                   | Yes    | 38 lines    | Yes   | VERIFIED   | idempotency lookup; findDueScheduledRequests; findPendingImmediateRequests  |
| `src/main/java/.../client/repo/ClientCreditBalanceRepository.java`                           | Yes    | 22 lines    | Yes   | VERIFIED   | findByClientIdForUpdate @Lock(PESSIMISTIC_WRITE) with 2000ms timeout       |
| `src/main/resources/db/migration/V5__send_request.sql`                                       | Yes    | 44 lines    | Yes   | VERIFIED   | UNIQUE(client_id, send_request_id); schedule_time, reservation_id columns  |
| `src/main/resources/db/migration/V6__send_request_finalized_at.sql`                          | Yes    | 5 lines     | Yes   | VERIFIED   | ALTER TABLE adds finalized_at column used by purge query                   |

---

## Key Link Verification

| From                      | To                                    | Via                                                     | Status  | Details                                                                      |
|---------------------------|---------------------------------------|---------------------------------------------------------|---------|------------------------------------------------------------------------------|
| SmsResource.sendSms       | SmsService.sendSms                    | @RequiredArgsConstructor injection                      | WIRED   | smsService.sendSms(clientId, request) at line 50                             |
| SmsResource.cancelScheduled | SmsService.cancelScheduled          | @RequiredArgsConstructor injection                      | WIRED   | smsService.cancelScheduled(clientId, sendRequestId) at line 79               |
| SmsService.sendSms (Step 1) | SendRequestRepository              | findByClientIdAndSendRequestId                          | WIRED   | Returns existing record for duplicate sendRequestId; early return at line 79 |
| SmsService.sendSms (Step 5) | CamMobileValidator                 | static CamMobileValidator.validate(phone)               | WIRED   | All recipients batch-validated at lines 112-119; all failures collected      |
| SmsService.sendSms (Step 8) | CreditReservationService.reserve() | creditReservationService.reserve(clientId, total, ref)  | WIRED   | Reserve called at line 144, after all validations at steps 1-7               |
| CreditReservationService.reserve | ClientCreditBalanceRepository | findByClientIdForUpdate (SELECT FOR UPDATE)             | WIRED   | Pessimistic write lock acquired at line 61; balance checked before deducting |
| SmsService.cancelScheduled | CreditReservationService.release() | creditReservationService.release(clientId, reservationId)| WIRED  | Called at line 253; only after guard confirms ACCEPTED + scheduleTime != null |
| SmsSchedulerService       | SendRequestRepository               | findDueScheduledRequests(now) + findPendingImmediateRequests() | WIRED | Both called at lines 53-54 in @Scheduled(fixedDelay=30_000) method         |
| ApiAdvice                 | SmsValidationException              | @ExceptionHandler(SmsValidationException.class) → 400  | WIRED   | Extracts errorCode from exception.getErrorCode().getErrorCode()              |
| ApiAdvice                 | CancelNotAllowedException           | @ExceptionHandler(CancelNotAllowedException.class) → 409| WIRED  | Hard-wired CANCEL_NOT_ALLOWED in exception constructor                       |
| ApiAdvice                 | RateLimitExceededException          | @ExceptionHandler(RateLimitExceededException.class) → 429| WIRED | Separate handler from AuthorizationException; 401 path unchanged             |
| ApiAdvice                 | InsufficientBalanceException        | @ExceptionHandler(InsufficientBalanceException.class) → 400| WIRED| Returns INSUFFICIENT_CLIENT_BALANCE error code                               |

---

## Requirements Coverage

| Requirement | Truth # | Status     | Verified By                                                                      |
|-------------|---------|------------|----------------------------------------------------------------------------------|
| SMS-01      | 1       | SATISFIED  | SmsResource POST /v1/sms/send; @NotEmpty @Size(max=1000) recipients list         |
| SMS-02      | 2       | SATISFIED  | Batch-collect invalid phones; all-or-nothing guard; validations before reserve() |
| SMS-03      | 3       | SATISFIED  | SELECT FOR UPDATE in CreditReservationService; SmsService @Transactional         |
| SMS-04      | 4       | SATISFIED  | scheduleTime on SendSmsRequest; Step 6 validates future; credits reserved Step 8 |
| SMS-05      | 5       | SATISFIED  | cancelScheduled guards ACCEPTED+scheduleTime; calls release(); sets CANCELLED    |
| SMS-06      | 6       | SATISFIED  | Step 1 idempotency check; returns toResponse(existing) without touching credits  |

---

## Anti-Patterns Found

| File            | Line | Pattern                                      | Severity | Impact                                                                                                                                                        |
|-----------------|------|----------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SmsService.java | 108  | Wrong error code for blank-message validation | Warning  | `throw new SmsValidationException("Message must not be blank", SmsError.INVALID_SENDER_ID)` — HTTP status is correct (400) but error_code in the response body says INVALID_SENDER_ID instead of a message-specific code. Non-blocking for phase goal. |
| SmsResource.java | 47  | Duplicate @RateLimited on send path          | Warning  | @RateLimited present on both SmsResource.sendSms (line 47) and SmsService.sendSms (line 71) — the 10 req/s AOP aspect fires twice per request. Functional but wastes rate-limit tokens. Non-blocking for phase goal. |

No blocker anti-patterns. Both warnings were noted in the prior verification and are pre-existing; no new regressions introduced.

---

## Atomicity Confirmation (SMS-03 Deep-Check)

Both `SmsService` and `CreditReservationService` are annotated `@Transactional`. Spring's default propagation is `REQUIRED`, so when `SmsService.sendSms()` calls `CreditReservationService.reserve()`, the reservation and the subsequent `SendRequest` / `SendRequestRecipient` saves all participate in the same outer transaction. A rollback at any point (e.g., DB error during recipient-row persistence) will undo both the ledger entry and the balance deduction. The pessimistic lock (`SELECT FOR UPDATE` with a 2-second timeout, guarded by both `LockTimeoutException` and `CannotAcquireLockException` handlers in ApiAdvice returning HTTP 503) ensures two concurrent reserves for the same client cannot both succeed when the balance is only sufficient for one.

---

## Validation-Before-Reservation Ordering Confirmation (SMS-02 Deep-Check)

The send pipeline in `SmsService.sendSms()`:
1. Idempotency check (Step 1)
2. Recipient count rate limit (Step 2)
3. Sender ID validation (Step 3)
4. Message blank-check (Step 4)
5. **All** recipient phone validation — batch-collects every invalid number before throwing (Step 5)
6. scheduleTime future-check (Step 6)
7. Circuit-breaker / provider availability check (Step 6.5)
8. Segment calculation (Step 7)
9. **Credit reservation** (Step 8) — first point any credits are touched
10. SendRequest row persistence (Step 9)
11. Recipient rows persistence (Step 10)

This order is verified line-by-line in `SmsService.java` lines 74-171. Credits are never reserved when any validation at steps 1-7 fails, satisfying the all-or-nothing requirement.

---

## Human Verification Required

None. All behavioral properties (HTTP status codes, error code values, ordering of validation vs. reservation, pessimistic lock wiring, idempotency guard, cancel guard) are verifiable from source code.

---

_Verified: 2026-03-11T02:51:09Z_
_Verifier: Claude (gsd-verifier)_
