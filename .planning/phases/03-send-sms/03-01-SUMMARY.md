---
phase: 03-send-sms
plan: "01"
subsystem: database
tags: [flyway, jpa, spring-security, gsm7, sms, segment-calculator]

# Dependency graph
requires:
  - phase: 02-credit-ledger-topups
    provides: CreditReservationService.reserve/release/debit; CreditLedgerEntry.id as reservationId
  - phase: 01-client-api-key-auth
    provides: ApiKeyAuthenticationFilter, ClientSecurityConfiguration, AbstractAuditingEntity pattern

provides:
  - V5 Flyway migration: send_request and send_request_recipient tables
  - SendRequest JPA entity with sendStatus mapped to send_status (no Hibernate collision)
  - SendRequestRecipient JPA entity with per-recipient delivery tracking
  - SendRequestRepository with findByClientIdAndSendRequestId and findDueScheduledRequests JPQL
  - SendRequestRecipientRepository with findBySendRequestIdFk queries
  - SendRequestStatus enum (7 states: ACCEPTED through FAIL_FINALIZED and CANCELLED)
  - SmsError enum implementing ErrorCode (4 SMS-specific error codes)
  - 5 contract records: SendSmsRequest, SendSmsResponse, CancelSmsResponse, MessageStatusEntry, MessageStatusResponse
  - SmsSegmentCalculator: GSM-7 basic charset detection with 160/153 and 70/67 thresholds
  - ApiAdvice: LockTimeoutException and CannotAcquireLockException handlers returning HTTP 503
  - ClientSecurityConfiguration securityMatcher widened to /v1/** (was /v1/api/**)

affects:
  - 03-02-send-sms (SmsService, SmsResource — depend on all entities and contract types)
  - 03-03-send-sms (cancel endpoint — depends on SendRequest.reservationId and CancelSmsResponse)
  - 04-provider-integration (needs SendRequest.reservationId for CreditReservationService.debit())

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Separate lifecycle status column (send_status) alongside inherited status column — same as TopupRequestEntity.topupStatus"
    - "Package-private final utility class with static method for deterministic calculation (SmsSegmentCalculator)"
    - "AppEndpoints constants as single source of truth for security matchers — ClientSecurityConfiguration uses AppEndpoints.CLIENT_API"
    - "Dual @ExceptionHandler for both JPA spec (LockTimeoutException) and Spring translation (CannotAcquireLockException)"

key-files:
  created:
    - src/main/resources/db/migration/V5__send_request.sql
    - src/main/java/com/softropic/sendam/client/contract/SendRequestStatus.java
    - src/main/java/com/softropic/sendam/client/contract/exception/SmsError.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequest.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRecipient.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRepository.java
    - src/main/java/com/softropic/sendam/client/repo/SendRequestRecipientRepository.java
    - src/main/java/com/softropic/sendam/client/contract/SendSmsRequest.java
    - src/main/java/com/softropic/sendam/client/contract/SendSmsResponse.java
    - src/main/java/com/softropic/sendam/client/contract/CancelSmsResponse.java
    - src/main/java/com/softropic/sendam/client/contract/MessageStatusEntry.java
    - src/main/java/com/softropic/sendam/client/contract/MessageStatusResponse.java
    - src/main/java/com/softropic/sendam/client/service/SmsSegmentCalculator.java
  modified:
    - src/main/java/com/softropic/sendam/security/api/ApiAdvice.java
    - src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java
    - src/main/java/com/softropic/sendam/security/config/AppEndpoints.java

key-decisions:
  - "SmsSegmentCalculator is package-private (not public) — only SmsService needs it; exposed as package-scoped utility within client.service"
  - "AppEndpoints.CLIENT_API updated to /v1/** — ClientSecurityConfiguration uses the constant, not a hardcoded string, to maintain single source of truth"
  - "Both LockTimeoutException (JPA spec) and CannotAcquireLockException (Spring translation) handled — Spring's exception translation layer may wrap Hibernate's LockTimeoutException"
  - "SmsError does NOT duplicate INSUFFICIENT_CLIENT_BALANCE — ClientError.INSUFFICIENT_CLIENT_BALANCE is reused for balance failures; SmsError owns only SMS-specific codes"

patterns-established:
  - "Pattern: lifecycle status on separate column (send_status) beside inherited entity status (status) — avoids Hibernate column collision on AbstractAuditingEntity"
  - "Pattern: JPQL query with fully-qualified enum reference in @Query for SendRequestRepository.findDueScheduledRequests"

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 3 Plan 01: Phase 3 Foundation Summary

**Flyway V5 schema (send_request + send_request_recipient), JPA entities, GSM-7 segment calculator, 5 contract records, dual LockTimeout 503 handlers, and security chain widened to /v1/**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-10T18:21:34Z
- **Completed:** 2026-03-10T18:29:41Z
- **Tasks:** 2
- **Files modified:** 16 (13 created, 3 modified)

## Accomplishments
- Database foundation: V5 migration creates send_request (with UNIQUE constraint and partial scheduler index) and send_request_recipient tables with correct audit columns
- Entity layer: SendRequest and SendRequestRecipient JPA entities using separate send_status column — no Hibernate collision with inherited status from AbstractAuditingEntity
- Contract layer: All 5 response/request records with correct @JsonProperty annotations; SendRequestStatus (7 states) and SmsError (4 codes) enums
- Utility: SmsSegmentCalculator with full GSM 03.38 basic charset; handles GSM-7 (160/153 thresholds) and UCS-2 (70/67 thresholds)
- Infrastructure: Two LockTimeoutException variants (JPA + Spring translation) handled in ApiAdvice → HTTP 503
- Security fix: ClientSecurityConfiguration securityMatcher widened from /v1/api/** to /v1/** — /v1/credits/** and /v1/sms/** are now properly protected by API key auth chain

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migration and JPA entities + repositories** - `164b25e` (feat)
2. **Task 2: Contract records, segment calculator, ApiAdvice update, security chain widen** - `fa9c875` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/db/migration/V5__send_request.sql` - send_request and send_request_recipient DDL with UNIQUE constraint, partial index
- `src/main/java/com/softropic/sendam/client/contract/SendRequestStatus.java` - 7-state SMS lifecycle enum
- `src/main/java/com/softropic/sendam/client/contract/exception/SmsError.java` - SMS-specific ErrorCode enum
- `src/main/java/com/softropic/sendam/client/repo/SendRequest.java` - JPA entity; sendStatus → send_status column
- `src/main/java/com/softropic/sendam/client/repo/SendRequestRecipient.java` - Per-recipient JPA entity
- `src/main/java/com/softropic/sendam/client/repo/SendRequestRepository.java` - findByClientIdAndSendRequestId, findDueScheduledRequests
- `src/main/java/com/softropic/sendam/client/repo/SendRequestRecipientRepository.java` - findBySendRequestIdFk queries
- `src/main/java/com/softropic/sendam/client/contract/SendSmsRequest.java` - POST /v1/sms/send body record
- `src/main/java/com/softropic/sendam/client/contract/SendSmsResponse.java` - 200 ACCEPTED response record
- `src/main/java/com/softropic/sendam/client/contract/CancelSmsResponse.java` - DELETE response record
- `src/main/java/com/softropic/sendam/client/contract/MessageStatusEntry.java` - Per-recipient status in GET response
- `src/main/java/com/softropic/sendam/client/contract/MessageStatusResponse.java` - GET status response record
- `src/main/java/com/softropic/sendam/client/service/SmsSegmentCalculator.java` - GSM-7/UCS-2 segment calculation utility
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java` - Added LockTimeoutException and CannotAcquireLockException handlers
- `src/main/java/com/softropic/sendam/client/config/ClientSecurityConfiguration.java` - Widened securityMatcher to AppEndpoints.CLIENT_API (/v1/**)
- `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` - CLIENT_API updated from /v1/api/** to /v1/**

## Decisions Made

- **SmsSegmentCalculator visibility:** Package-private (not public) — only SmsService within the same package needs it; keeps the utility scoped appropriately.
- **AppEndpoints.CLIENT_API as single source of truth:** Rather than hardcoding `/v1/**` in ClientSecurityConfiguration, the constant in AppEndpoints was updated and the configuration uses the constant. This ensures the security matcher path cannot drift from the documented constant.
- **Dual LockTimeout handlers:** Both `jakarta.persistence.LockTimeoutException` (JPA spec, thrown directly) and `org.springframework.dao.CannotAcquireLockException` (Spring's exception translation wrapper) are handled. The research doc noted uncertainty about which variant Hibernate throws with the `jakarta.persistence.lock.timeout` query hint; handling both is defensive correctness.
- **SmsError does not duplicate INSUFFICIENT_CLIENT_BALANCE:** Per the plan, ClientError.INSUFFICIENT_CLIENT_BALANCE remains the canonical error code for balance failures; SmsError owns only SMS-specific codes (phone number, sender ID, schedule time, cancel state).

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. The `mvnw` wrapper script was missing the `.mvn/wrapper/` directory, so `mvn` (system Maven) was used instead. This is a pre-existing environment condition and did not affect the output artifacts.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

All foundation artifacts for Phase 3 are in place:
- Plan 03-02 (SmsService + SmsResource) can now proceed: entities, repositories, contract types, and segment calculator are all available
- Plan 03-03 (cancel + scheduler) can proceed after 03-02: CancelSmsResponse and SendRequest.reservationId are available
- The security chain widening in this plan also retroactively fixes /v1/credits/** coverage — credit/topup endpoints are now correctly behind API key auth

No blockers for 03-02 or 03-03.

---
*Phase: 03-send-sms*
*Completed: 2026-03-10*
