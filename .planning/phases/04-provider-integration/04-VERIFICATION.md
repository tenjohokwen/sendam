---
phase: 04-provider-integration
verified: 2026-03-11T02:57:50Z
status: passed
score: 6/6 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 6/6
  gaps_closed: []
  gaps_remaining: []
  regressions: []
---

# Phase 4: Provider Integration Verification Report

**Phase Goal:** Close the delivery loop — submit messages to Nexah, ingest delivery reports, advance the state machine, settle billing on provider-confirmed segment counts, and expose status queries to clients.
**Verified:** 2026-03-11T02:57:50Z
**Status:** passed
**Re-verification:** Yes — independent re-verification against source files (not trusting prior SUMMARY claims)

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Accepted messages are submitted to Nexah; each gets a gateway_message_id and moves to SUBMITTED state | VERIFIED | `NexahDispatchService.dispatch()` calls `nexahClient.sendSms()` (@CircuitBreaker), maps response by `mobileNo`, sets `recipient.gatewayMessageId = entry.messageId()`, `recipient.providerMessageId = entry.smsClientId()`, `recipient.sendStatus = SUBMITTED`, then `request.sendStatus = SUBMITTED`. Called from `SmsSchedulerService.dispatchScheduledMessages()` every 30s. |
| 2 | Nexah delivery reports are ingested; messages advance to FINALIZED or FAIL_FINALIZED | VERIFIED | `DrCallbackResource POST /v1/provider/dr` delegates to `DrCallbackService.processDr()`. DELIVRD → `COMPLETED`, else → `FAILED` on recipient. `finalizeParentIfAllTerminal()` sets parent to `FINALIZED` (all COMPLETED) or `FAIL_FINALIZED` (any FAILED) when all recipients are terminal. Sets `parent.finalizedAt = Instant.now()`. |
| 3 | Billing is settled on provider-confirmed segment count; over-reservation generates an SMS_REFUND ledger entry | VERIFIED | `DrCallbackService.finalizeParentIfAllTerminal()` calls `creditReservationService.debit(parent.getClientId(), parent.getReservationId(), debitable)` where `debitable = min(totalActualSegments, reservedCredits)`. `CreditReservationService.debit()` writes `SMS_DEBIT` entry; if `overReservation > 0` writes a second `SMS_REFUND` entry and restores balance. `LedgerEntryType.SMS_REFUND` exists in enum. |
| 4 | When Nexah is unavailable, new send requests are rejected with PROVIDER_UNAVAILABLE and no credits are touched | VERIFIED | `SmsService.sendSms()` checks `cb.getState() == OPEN \|\| HALF_OPEN` at step 6.5 (lines 132-135), **before** `creditReservationService.reserve()` at step 8 (line 144). Throws `ProviderUnavailableException` → `ApiAdvice.providerUnavailableHandler()` @ExceptionHandler → HTTP 503 `PROVIDER_UNAVAILABLE`. No ledger entry written when circuit is not CLOSED. |
| 5 | Client can query per-recipient delivery status by sendRequestId with pagination | VERIFIED | `SmsResource GET /v1/sms/status/{sendRequestId}` delegates to `SmsService.getStatus(clientId, sendRequestId, page, pageSize)`. Returns `MessageStatusResponse` with `page`, `pageSize` (clamped to 200), `totalMessages`, and a `List<MessageStatusEntry>` each carrying `recipient`, `state`, `gateway_message_id`, `provider_message_id`, `segments_consumed`. |
| 6 | Message status records are purged 30 days after finalization; queries return 404 after the window | VERIFIED | `SmsPurgeService.purgeOldMessages()` with `cron = "0 0 2 * * *"` computes `cutoff = now - 30 days`, calls `findFinalizedBefore(cutoff)` (JPQL query on `finalizedAt` + FINALIZED/FAIL_FINALIZED statuses), then deletes recipients first and parents second in batches of 500. After purge, `SmsService.getStatus()` calls `findByClientIdAndSendRequestId` which returns empty → `ResourceNotFoundException` → `ApiAdvice.resourceNotFoundExceptionHandler()` → HTTP 404. |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Purpose | Exists | Lines | Stubs | Wired |
|----------|---------|--------|-------|-------|-------|
| `client/infrastructure/nexah/NexahClient.java` | Outbound HTTP to Nexah, circuit breaker | YES | 117 | NONE | Called by NexahDispatchService |
| `client/service/NexahDispatchService.java` | ACCEPTED → SUBMITTED, sets gateway_message_id | YES | 118 | NONE | Called by SmsSchedulerService |
| `client/service/DrCallbackService.java` | DR ingestion, state machine, billing settlement | YES | 256 | NONE | Called by DrCallbackResource + SmsSchedulerService |
| `client/api/DrCallbackResource.java` | POST /v1/provider/dr endpoint | YES | 45 | NONE | Routes to DrCallbackService.processDr() |
| `client/service/SmsService.java` | Circuit breaker guard before reserve(); getStatus() pagination | YES | 290 | NONE | Called by SmsResource |
| `client/service/SmsSchedulerService.java` | 30s dispatch + 1h stale recovery | YES | 109 | NONE | @Scheduled beans active via @EnableScheduling on ClientConfig |
| `client/service/SmsPurgeService.java` | 30-day nightly purge | YES | 65 | NONE | @Scheduled bean active |
| `client/service/CreditReservationService.java` | debit() with SMS_DEBIT + SMS_REFUND on over-reservation | YES | 233 | NONE | Called by DrCallbackService |
| `client/api/SmsResource.java` | GET /v1/sms/status/{id} with pagination | YES | 77 | NONE | Calls SmsService.getStatus() |
| `client/contract/exception/ProviderUnavailableException.java` | Typed exception for OPEN circuit | YES | 17 | NONE | Thrown by NexahClient fallback, caught by ApiAdvice |
| `client/config/NexahSecurityConfiguration.java` | @Order(0) permit /v1/provider/** | YES | 35 | NONE | Registered as SecurityFilterChain |
| `client/config/ClientConfig.java` | @EnableScheduling + NexahProperties binding | YES | 17 | NONE | Required for all @Scheduled beans |
| `db/migration/V6__send_request_finalized_at.sql` | finalized_at column + partial index | YES | 5 | NONE | Flyway migration |
| `client/repo/SendRequestRepository.java` | findFinalizedBefore, deleteAllByIdIn, findPendingImmediateRequests, findStaleSubmittedRequests | YES | 38 | NONE | Used by SmsPurgeService and SmsSchedulerService |
| `client/repo/SendRequestRecipientRepository.java` | findByGatewayMessageId, deleteBySendRequestIdFkIn | YES | 22 | NONE | Used by DrCallbackService and SmsPurgeService |
| `client/contract/LedgerEntryType.java` | SMS_REFUND enum value | YES | 9 | NONE | Used by CreditReservationService.debit() |
| `client/contract/SendRequestStatus.java` | ACCEPTED/SUBMITTED/COMPLETED/FAILED/FINALIZED/FAIL_FINALIZED | YES | 11 | NONE | Used throughout state machine |
| `client/contract/MessageStatusResponse.java` | Paginated status response DTO | YES | 14 | NONE | Returned by SmsResource.getStatus() |
| `client/contract/MessageStatusEntry.java` | Per-recipient status DTO with all 5 required fields | YES | 11 | NONE | Populated by SmsService.getStatus() |
| `security/api/ApiAdvice.java` | providerUnavailableHandler → HTTP 503 PROVIDER_UNAVAILABLE | YES | 545 | NONE | @RestControllerAdvice, handles ProviderUnavailableException |

---

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `SmsSchedulerService.dispatchScheduledMessages()` | `NexahDispatchService.dispatch()` | Direct call in 30s @Scheduled loop | WIRED |
| `NexahDispatchService` | `NexahClient.sendSms()` | Direct call; @CircuitBreaker(name="nexah") on NexahClient method | WIRED |
| `NexahClient.sendSmsFallback()` | `ProviderUnavailableException` | Fallback method throws exception when circuit OPEN | WIRED |
| `DrCallbackResource POST /v1/provider/dr` | `DrCallbackService.processDr()` | `drCallbackService.processDr(payload)` | WIRED |
| `DrCallbackService.finalizeParentIfAllTerminal()` | `CreditReservationService.debit()` | `creditReservationService.debit(parent.getClientId(), parent.getReservationId(), debitable)` | WIRED |
| `CreditReservationService.debit()` | SMS_DEBIT + SMS_REFUND ledger entries | `if (overReservation > 0)` writes `LedgerEntryType.SMS_REFUND` after writing `SMS_DEBIT` | WIRED |
| `SmsService.sendSms()` | circuit breaker state check | `circuitBreakerRegistry.circuitBreaker("nexah")` checked at step 6.5 before reserve() at step 8 | WIRED |
| `SmsService.sendSms()` | `ProviderUnavailableException` → HTTP 503 | `ApiAdvice.providerUnavailableHandler()` @ExceptionHandler(ProviderUnavailableException.class) @ResponseStatus(SERVICE_UNAVAILABLE) | WIRED |
| `SmsResource GET /v1/sms/status/{id}` | `SmsService.getStatus()` | `smsService.getStatus(clientId, sendRequestId, page, pageSize)` | WIRED |
| `SmsService.getStatus()` | `ResourceNotFoundException` on missing row | `findByClientIdAndSendRequestId(...).orElseThrow(() -> new ResourceNotFoundException(...))` | WIRED |
| `ResourceNotFoundException` | HTTP 404 | `ApiAdvice.resourceNotFoundExceptionHandler()` @ResponseStatus(NOT_FOUND) | WIRED |
| `SmsPurgeService.purgeOldMessages()` | `findFinalizedBefore(cutoff)` + batch delete | `recipientRepository.deleteBySendRequestIdFkIn(batch)` then `sendRequestRepository.deleteAllByIdIn(batch)` | WIRED |
| `SmsSchedulerService.recoverStaleSms()` | `DrCallbackService.forceFinalizeStaleSms()` | `drCallbackService.forceFinalizeStaleSms(request.getId())` in hourly @Scheduled loop | WIRED |
| Resilience4j `nexah` circuit breaker | `NexahClient.sendSms()` | `application.yaml` `resilience4j.circuitbreaker.instances.nexah` with slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s | WIRED |
| `/v1/provider/**` | No API key required | `NexahSecurityConfiguration @Order(0)` scoped to `/v1/provider/**` with `permitAll()` | WIRED |

---

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| PROVIDER-01: Gateway forwards validated SMS requests to Nexah | SATISFIED | NexahDispatchService + NexahClient + SmsSchedulerService dispatch loop |
| PROVIDER-02: Gateway ingests Nexah DR callbacks, advances state machine | SATISFIED | DrCallbackResource + DrCallbackService.processDr() + state machine in finalizeParentIfAllTerminal() |
| PROVIDER-03: When Nexah unavailable, reject with PROVIDER_UNAVAILABLE; credits unchanged | SATISFIED | SmsService step 6.5 circuit breaker check before step 8 reserve(); ApiAdvice HTTP 503 |
| SMS-07: Credits debited using provider-reported segment count | SATISFIED | DrCallbackService parses totalSmsUnit from DR, sets segmentsConsumed on recipient, sums actualSegments, calls debit() with actual count |
| STATUS-01: Client can query delivery status by sendRequestId (paginated) | SATISFIED | SmsResource GET /v1/sms/status/{sendRequestId} with page/pageSize params; SmsService.getStatus() |
| STATUS-02: State machine ACCEPTED → SUBMITTED → COMPLETED/FAILED → FINALIZED/FAIL_FINALIZED | SATISFIED | SendRequestStatus enum has all states; transitions enforced in NexahDispatchService (→SUBMITTED), DrCallbackService (→COMPLETED/FAILED→FINALIZED/FAIL_FINALIZED) |
| STATUS-03: Status purged 30 days after finalization; 404 after window | SATISFIED | SmsPurgeService nightly cron + findFinalizedBefore(30-day cutoff) + 404 via ResourceNotFoundException handler |

---

### Anti-Patterns Found

None. Zero TODO/FIXME/placeholder/stub/return-null patterns found across all 9 scanned implementation files (NexahClient, NexahDispatchService, DrCallbackService, CreditReservationService, SmsService, SmsPurgeService, SmsSchedulerService, DrCallbackResource, SmsResource).

---

### Human Verification Required

The following behaviors require runtime or integration observation and cannot be verified statically:

#### 1. Circuit Breaker State Transition Under Load

**Test:** Call `POST /v1/sms/send` while `NEXAH_USER`/`NEXAH_PASSWORD` point to an unreachable server. After 5 consecutive send failures (exceeding 50% of sliding window of 10), verify that subsequent send requests return HTTP 503 with `error_code: PROVIDER_UNAVAILABLE` without touching the ledger.
**Expected:** HTTP 503, error_code=PROVIDER_UNAVAILABLE. No SMS_RESERVATION entry in credit_ledger.
**Why human:** Circuit breaker state (OPEN/HALF_OPEN/CLOSED) transitions require real call failures to trigger; cannot be asserted from static code.

#### 2. DR Callback Billing Settlement — Over-Reservation Refund

**Test:** Send a multi-recipient request, then POST a DR callback where `total_sms_unit` for some recipients is lower than the calculated segment count at reservation time. Verify: (a) parent reaches FINALIZED/FAIL_FINALIZED, (b) SMS_DEBIT ledger entry exists with the actual count, (c) SMS_REFUND ledger entry exists for the difference, (d) client's available balance increased by the refund amount.
**Expected:** Two ledger entries written (SMS_DEBIT + SMS_REFUND), balance restored for over-reservation.
**Why human:** Requires a running database and verifying ledger row values at runtime.

#### 3. 404 After 30-Day Purge

**Test:** Manually set a FINALIZED send_request's `finalized_at` to 31 days ago, trigger `SmsPurgeService.purgeOldMessages()`, then call `GET /v1/sms/status/{sendRequestId}`.
**Expected:** HTTP 404 response.
**Why human:** Requires database manipulation and triggering the scheduled job out-of-band.

#### 4. Duplicate DR Idempotency

**Test:** Send the same DR callback payload twice for the same `messageId`. Verify: first call advances state and settles billing; second call returns status=1 (idempotent ack) without creating a second SMS_DEBIT entry.
**Expected:** Exactly one SMS_DEBIT ledger entry after two identical DR callbacks.
**Why human:** Requires runtime state and DB inspection.

---

## Summary

All six must-have truths are independently verified against actual source files. The full delivery loop is implemented end-to-end with no stubs, placeholders, or orphaned artifacts:

- **Dispatch path:** `SmsSchedulerService` (@Scheduled 30s) → `NexahDispatchService.dispatch()` → `NexahClient.sendSms()` (@CircuitBreaker, fallback throws ProviderUnavailableException) → recipients set to SUBMITTED with `gatewayMessageId` and `providerMessageId` from Nexah response, indexed by `mobileNo`.
- **DR ingestion path:** `DrCallbackResource POST /v1/provider/dr` (permitAll via NexahSecurityConfiguration @Order(0)) → `DrCallbackService.processDr()` → per-recipient SUBMITTED → COMPLETED (DELIVRD) / FAILED → `finalizeParentIfAllTerminal()` → `CreditReservationService.debit()` with `min(actualSegments, reservedCredits)` (writes SMS_DEBIT + conditional SMS_REFUND) → parent FINALIZED/FAIL_FINALIZED + `finalizedAt` timestamp set.
- **Provider unavailability guard:** `SmsService.sendSms()` step 6.5 checks `circuitBreakerRegistry.circuitBreaker("nexah").getState()` in-memory before step 8 `reserve()`. `ProviderUnavailableException` → `ApiAdvice.providerUnavailableHandler()` → HTTP 503 PROVIDER_UNAVAILABLE. Balance is untouched.
- **Status query:** `SmsResource GET /v1/sms/status/{sendRequestId}` → `SmsService.getStatus()` with pageSize clamped to 200. Returns `MessageStatusResponse` with 5-field `MessageStatusEntry` per recipient.
- **Purge and 404:** `SmsPurgeService.purgeOldMessages()` (cron 02:00 daily) deletes FINALIZED rows with `finalizedAt < now-30days`. After purge, `getStatus()` throws `ResourceNotFoundException` → `ApiAdvice.resourceNotFoundExceptionHandler()` → HTTP 404.
- **Stale recovery:** Hourly `SmsSchedulerService.recoverStaleSms()` force-finalizes SUBMITTED requests stuck 24+ hours by calling `drCallbackService.forceFinalizeStaleSms()`.
- **Resilience4j config:** `application.yaml` configures `nexah` circuit breaker with `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`.
- **Scheduling infrastructure:** `ClientConfig @EnableScheduling` activates both @Scheduled beans.

No regressions from prior verification. Four items are flagged for human integration testing (runtime behaviors requiring a live database and real or simulated Nexah responses).

---

_Verified: 2026-03-11T02:57:50Z_
_Verifier: Claude (gsd-verifier)_
_Mode: Re-verification (independent source-file check — prior SUMMARY not trusted)_
