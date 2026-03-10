---
phase: 04-provider-integration
verified: 2026-03-10T20:07:09Z
status: passed
score: 6/6 must-haves verified
---

# Phase 4: Provider Integration Verification Report

**Phase Goal:** Close the delivery loop — submit messages to Nexah, ingest delivery reports, advance the state machine, settle billing on provider-confirmed segment counts, and expose status queries to clients.
**Verified:** 2026-03-10T20:07:09Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Accepted messages are submitted to Nexah; each gets a gateway_message_id and moves to SUBMITTED state | VERIFIED | `NexahDispatchService.dispatch()` calls `NexahClient.sendSms()`, maps response by mobileNo, sets `gatewayMessageId`/`providerMessageId` on each `SendRequestRecipient`, sets `sendStatus=SUBMITTED` on both recipient and parent. Called by `SmsSchedulerService` every 30s. |
| 2 | Nexah delivery reports are ingested; messages advance to FINALIZED or FAIL_FINALIZED | VERIFIED | `DrCallbackService.processDr()` wired into `DrCallbackResource POST /v1/provider/dr`. DELIVRD → COMPLETED, else → FAILED. `finalizeParentIfAllTerminal()` advances parent to FINALIZED/FAIL_FINALIZED when all recipients are terminal. |
| 3 | Billing is settled on provider-confirmed segment count; over-reservation generates an SMS_REFUND ledger entry | VERIFIED | `CreditReservationService.debit()` writes `SMS_DEBIT` entry; if `overReservation > 0` writes a second `SMS_REFUND` entry and restores balance. `DrCallbackService` calls `debit(clientId, reservationId, debitable)` where `debitable = Math.min(totalActualSegments, reservedCredits)`. `LedgerEntryType.SMS_REFUND` exists in the enum. |
| 4 | When Nexah is unavailable, new send requests are rejected with PROVIDER_UNAVAILABLE and no credits are touched | VERIFIED | `SmsService.sendSms()` checks `CircuitBreaker.State.OPEN \|\| HALF_OPEN` (step 6.5) **before** `creditReservationService.reserve()` (step 8). Throws `ProviderUnavailableException` → `ApiAdvice.providerUnavailableHandler()` → HTTP 503 `PROVIDER_UNAVAILABLE`. No ledger entry is written if the circuit is open. |
| 5 | Client can query per-recipient delivery status by sendRequestId with pagination | VERIFIED | `SmsResource GET /v1/sms/status/{sendRequestId}` delegates to `SmsService.getStatus()`. Returns `MessageStatusResponse` with pagination (`page`, `pageSize` clamped to 200, `total`). Each `MessageStatusEntry` carries `recipient`, `state`, `gateway_message_id`, `provider_message_id`, `segments_consumed`. |
| 6 | Message status records are purged 30 days after finalization; queries return 404 after the window | VERIFIED | `SmsPurgeService.purgeOldMessages()` scheduled `cron = "0 0 2 * * *"` targets `findFinalizedBefore(cutoff)` where cutoff = `now - 30 days`. Deletes recipients before parents (FK-safe), batches of 500. After purge, `SmsService.getStatus()` hits `findByClientIdAndSendRequestId` which returns empty → `ResourceNotFoundException` → `ApiAdvice.resourceNotFoundExceptionHandler()` → HTTP 404. |

**Score:** 6/6 truths verified

---

### Required Artifacts

| Artifact | Purpose | Exists | Lines | Stubs | Wired |
|----------|---------|--------|-------|-------|-------|
| `client/infrastructure/nexah/NexahClient.java` | Outbound HTTP to Nexah, circuit breaker | YES | 117 | NONE | Called by NexahDispatchService |
| `client/service/NexahDispatchService.java` | ACCEPTED → SUBMITTED, sets gateway_message_id | YES | 118 | NONE | Called by SmsSchedulerService |
| `client/service/DrCallbackService.java` | DR ingestion, state machine, billing settlement | YES | 241 | NONE | Called by DrCallbackResource + SmsSchedulerService |
| `client/api/DrCallbackResource.java` | POST /v1/provider/dr endpoint | YES | 45 | NONE | Routes to DrCallbackService |
| `client/service/SmsService.java` | Circuit breaker guard before reserve() | YES | 290 | NONE | Called by SmsResource |
| `client/service/SmsSchedulerService.java` | 30s dispatch + 1h stale recovery | YES | 109 | NONE | @Scheduled beans active |
| `client/service/SmsPurgeService.java` | 30-day nightly purge | YES | 65 | NONE | @Scheduled bean active |
| `client/service/CreditReservationService.java` | debit() + SMS_REFUND on over-reservation | YES | 233 | NONE | Called by DrCallbackService |
| `client/api/SmsResource.java` | GET /v1/sms/status/{id} with pagination | YES | 82 | NONE | Calls SmsService.getStatus() |
| `client/contract/exception/ProviderUnavailableException.java` | Typed exception for OPEN circuit | YES | 17 | NONE | Thrown by NexahClient fallback, caught by ApiAdvice |
| `client/config/NexahSecurityConfiguration.java` | @Order(0) permit /v1/provider/** | YES | 35 | NONE | Registered as SecurityFilterChain |
| `db/migration/V6__send_request_finalized_at.sql` | finalized_at column + partial index | YES | 6 | NONE | Flyway migration |
| `client/repo/SendRequestRepository.java` | findFinalizedBefore, deleteAllByIdIn, findPendingImmediateRequests, findStaleSubmittedRequests | YES | 38 | NONE | Used by SmsPurgeService and SmsSchedulerService |
| `client/repo/SendRequestRecipientRepository.java` | findByGatewayMessageId, deleteBySendRequestIdFkIn | YES | 22 | NONE | Used by DrCallbackService and SmsPurgeService |

---

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `SmsSchedulerService` | `NexahDispatchService.dispatch()` | `nexahDispatchService.dispatch(request)` in 30s @Scheduled loop | WIRED |
| `NexahDispatchService` | `NexahClient.sendSms()` | Direct call, wrapped by @CircuitBreaker(name="nexah") | WIRED |
| `NexahClient` | ProviderUnavailableException (fallback) | `sendSmsFallback()` throws exception when circuit OPEN | WIRED |
| `DrCallbackResource POST /v1/provider/dr` | `DrCallbackService.processDr()` | `drCallbackService.processDr(payload)` | WIRED |
| `DrCallbackService` | `CreditReservationService.debit()` | `creditReservationService.debit(parent.getClientId(), parent.getReservationId(), debitable)` in `finalizeParentIfAllTerminal()` | WIRED |
| `CreditReservationService.debit()` | SMS_REFUND ledger entry | `if (overReservation > 0)` writes `LedgerEntryType.SMS_REFUND` | WIRED |
| `SmsService.sendSms()` | circuit breaker state check | `circuitBreakerRegistry.circuitBreaker("nexah")` checked before `reserve()` | WIRED |
| `SmsService.sendSms()` | ProviderUnavailableException → HTTP 503 | `ApiAdvice.providerUnavailableHandler()` @ExceptionHandler | WIRED |
| `SmsResource GET /v1/sms/status/{id}` | `SmsService.getStatus()` | `smsService.getStatus(clientId, sendRequestId, page, pageSize)` | WIRED |
| `SmsPurgeService` | `SendRequestRepository.findFinalizedBefore()` + `deleteAllByIdIn()` | Direct repo calls, child-before-parent batch | WIRED |
| Purge removes rows | 404 on status query | `findByClientIdAndSendRequestId` returns empty → `ResourceNotFoundException` → ApiAdvice 404 | WIRED |
| `SmsSchedulerService.recoverStaleSms()` | `DrCallbackService.forceFinalizeStaleSms()` | `drCallbackService.forceFinalizeStaleSms(request.getId())` hourly @Scheduled | WIRED |
| Resilience4j `nexah` circuit breaker | NexahClient | `application.yaml` `resilience4j.circuitbreaker.instances.nexah` configured, `@CircuitBreaker(name="nexah")` annotation | WIRED |
| `/v1/provider/**` | No API key required | `NexahSecurityConfiguration @Order(0)` scoped to `/v1/provider/**` with `permitAll()` | WIRED |

---

### Anti-Patterns Found

None. Zero TODO/FIXME/placeholder/stub patterns found across all 7 key implementation files (985 total lines scanned).

---

### Human Verification Required

The following behaviors require runtime or integration observation and cannot be verified statically:

#### 1. Circuit Breaker State Transition Under Load

**Test:** Call `POST /v1/sms/send` while `NEXAH_USER`/`NEXAH_PASSWORD` point to an unreachable server. After 5 consecutive send failures (>50% of sliding window of 10), verify that subsequent send requests return HTTP 503 with `error_code: PROVIDER_UNAVAILABLE` without touching the ledger.
**Expected:** HTTP 503, error_code=PROVIDER_UNAVAILABLE. No SMS_RESERVATION entry in credit_ledger.
**Why human:** Circuit breaker state (OPEN/HALF_OPEN/CLOSED) transitions require real call failures to trigger; cannot be asserted from static code.

#### 2. DR Callback Billing Settlement — Over-Reservation Refund

**Test:** Send a multi-recipient request, then POST a DR callback where `totalSmsUnit` for some recipients is lower than the calculated segment count at reservation time. Verify: (a) parent reaches FINALIZED/FAIL_FINALIZED, (b) SMS_DEBIT ledger entry exists with the actual count, (c) SMS_REFUND ledger entry exists for the difference, (d) client's available balance increased by the refund amount.
**Expected:** Two ledger entries written (SMS_DEBIT + SMS_REFUND), balance restored for over-reservation.
**Why human:** Requires a running database and verifying ledger row values at runtime.

#### 3. 404 After 30-Day Purge

**Test:** Manually set a FINALIZED send_request's `finalized_at` to 31 days ago, trigger `SmsPurgeService.purgeOldMessages()`, then call `GET /v1/sms/status/{sendRequestId}`.
**Expected:** HTTP 404 response.
**Why human:** Requires database manipulation and triggering the scheduled job out-of-band.

#### 4. Duplicate DR Idempotency

**Test:** Send the same DR callback payload twice for the same `messageId`. Verify: first call advances state and settles billing; second call returns status=1 without creating a second SMS_DEBIT entry.
**Expected:** Exactly one SMS_DEBIT ledger entry after two identical DR callbacks.
**Why human:** Requires runtime state and DB inspection.

---

## Summary

All six must-have truths are structurally verified. The full delivery loop is implemented end-to-end:

- **Dispatch path:** `SmsSchedulerService` → `NexahDispatchService.dispatch()` → `NexahClient.sendSms()` (@CircuitBreaker) → recipients set to SUBMITTED with `gatewayMessageId` and `providerMessageId`.
- **DR ingestion path:** `DrCallbackResource POST /v1/provider/dr` → `DrCallbackService.processDr()` → per-recipient state machine (SUBMITTED → COMPLETED/FAILED) → `finalizeParentIfAllTerminal()` → `CreditReservationService.debit()` (with SMS_REFUND on over-reservation) → parent FINALIZED/FAIL_FINALIZED.
- **Provider unavailability guard:** `SmsService` checks circuit breaker state in-memory before calling `reserve()`. `ProviderUnavailableException` → HTTP 503 PROVIDER_UNAVAILABLE mapped in `ApiAdvice`.
- **Status query:** `SmsResource GET /v1/sms/status/{sendRequestId}` delegates to `SmsService.getStatus()` with pagination (page/pageSize). Returns 5-field `MessageStatusEntry` per recipient.
- **Purge and 404:** `SmsPurgeService` nightly cron deletes FINALIZED rows older than 30 days. `getStatus()` returns 404 after rows are removed via existing `ResourceNotFoundException` handler.
- **Stale recovery:** Hourly `SmsSchedulerService.recoverStaleSms()` force-finalizes SUBMITTED requests stuck 24+ hours.

No stubs, no orphaned artifacts, no anti-patterns. Four items are flagged for human integration testing (runtime behaviors requiring a live database and real or simulated Nexah responses).

---

_Verified: 2026-03-10T20:07:09Z_
_Verifier: Claude (gsd-verifier)_
