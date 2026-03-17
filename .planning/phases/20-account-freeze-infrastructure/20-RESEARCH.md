# Phase 20: Account Freeze Infrastructure - Research

**Researched:** 2026-03-17
**Domain:** Account lifecycle management — client freeze/unfreeze, platform freeze/unfreeze, scheduled SMS suspension/resume
**Confidence:** HIGH

---

## Summary

Phase 20 introduces two freeze scopes: per-client freeze (CFREEZE) and platform-wide freeze (PFLAT). Both share the same lifecycle: freeze-on-shortfall or admin-initiated, suspend pending scheduled SMS, allow in-flight sends to complete, admin-unfreeze with mandatory note, auto-resume suspended SMS.

The codebase already has all the structural pieces needed. The key changes are: (1) add freeze columns to `ClientEntity` and a singleton platform-freeze record, (2) add a `SUSPENDED` status to `SendRequestStatus` for frozen scheduled sends, (3) intercept credit reservation in `CreditReservationService` to reject frozen states, (4) add freeze/unfreeze service methods, (5) expose admin REST endpoints, and (6) add `AuditEventType` entries.

**Primary recommendation:** Add freeze state directly to `ClientEntity` (new columns: `frozen`, `frozen_at`, `freeze_reason`) and a singleton `platform_freeze_state` table. Use `SUSPENDED` as a new `SendRequestStatus` value — it is the correct parallel to `CANCELLED` (existing) without permanently discarding the send. Freeze checks belong in `CreditReservationService.reserve()` — that is already the single serialization point for all credit reservations.

---

## Standard Stack

This phase is pure Spring Boot / JPA / PostgreSQL — no new libraries needed.

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Spring Data JPA `@Lock(PESSIMISTIC_WRITE)` | existing | SELECT FOR UPDATE on freeze state | Already used for credit balance rows — same pattern |
| Flyway migration | existing | Schema changes for freeze columns | V12 migration file |
| `AbstractAuditingEntity` / `BaseEntity` | existing | Entity base | All domain entities extend this |
| `ApplicationException` + `ErrorCode` | existing | Domain exception hierarchy | Existing pattern: `InsufficientBalanceException`, `CancelNotAllowedException` |
| `@PreAuthorize("hasRole('ADMIN')")` | existing | Endpoint security | Same as `AdminPlatformCreditResource` |
| `AppEndpoints` + `SECURED_MAPPINGS` | existing | URL-level security | Two new path constants needed |
| `AuditEventType` + `DomainAuditEvent` | existing | Admin action auditing | Same as `TOPUP_APPROVED`, `NEXAH_PURCHASE_RECORDED` |

### Supporting
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `@Modifying @Query` JPQL bulk update | Suspend/resume many `SendRequest` rows in one statement | Needed for bulk ACCEPTED→SUSPENDED and SUSPENDED→ACCEPTED transitions |
| `@Transactional(readOnly = true)` | Freeze-state read queries | Same as existing balance checks |

---

## Architecture Patterns

### Recommended Project Structure

New files follow the existing `gateway/` module pattern:

```
gateway/
├── account/
│   ├── api/
│   │   └── AdminClientFreezeResource.java      # NEW — freeze/unfreeze admin endpoints
│   ├── contract/
│   │   ├── ClientFreezeRequest.java            # NEW — freeze request body (reason)
│   │   ├── ClientUnfreezeRequest.java          # NEW — unfreeze request body (resolution note)
│   │   ├── ClientFreezeResponse.java           # NEW — freeze/unfreeze response DTO
│   │   └── AccountFrozenException.java         # NEW — thrown by CreditReservationService
│   └── service/
│       └── ClientFreezeService.java            # NEW — freeze/unfreeze business logic
├── billing/
│   └── contract/
│       └── PlatformFrozenException.java        # NEW — thrown by CreditReservationService
└── [no new billing service files — freeze check added to existing CreditReservationService]

gateway/
├── [platform freeze lives in billing module — it's a platform-level billing concern]
│   ├── api/
│   │   └── AdminPlatformFreezeResource.java    # NEW — platform freeze/unfreeze endpoints
│   ├── contract/
│   │   ├── PlatformFreezeRequest.java          # NEW
│   │   ├── PlatformFreezeResponse.java         # NEW
│   │   └── PlatformUnfreezeRequest.java        # NEW
│   └── service/
│       └── PlatformFreezeService.java          # NEW

src/main/resources/db/migration/
└── V12__account_freeze.sql                     # NEW — freeze columns + platform_freeze_state table
```

### Pattern 1: Freeze State on ClientEntity

`ClientEntity` already extends `AbstractAuditingEntity` which already has `status` (ACTIVE/INACTIVE/DELETED). Freeze is a separate lifecycle concern — **do not reuse `EntityStatus.INACTIVE`** for frozen state. Inactive means the account is deactivated administratively; frozen means a temporary billing hold.

Add three new columns directly to `client_account`:

```sql
-- V12__account_freeze.sql (partial)
ALTER TABLE main.client_account
    ADD COLUMN frozen           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN frozen_at        TIMESTAMP,
    ADD COLUMN freeze_reason    TEXT,
    ADD COLUMN freeze_resolved_at  TIMESTAMP,
    ADD COLUMN freeze_resolution   TEXT;
```

Corresponding Java fields on `ClientEntity`:

```java
@Column(name = "frozen", nullable = false)
@Builder.Default
private boolean frozen = false;

@Column(name = "frozen_at")
private Instant frozenAt;

@Column(name = "freeze_reason", columnDefinition = "TEXT")
private String freezeReason;

@Column(name = "freeze_resolved_at")
private Instant freezeResolvedAt;

@Column(name = "freeze_resolution", columnDefinition = "TEXT")
private String freezeResolution;
```

**Why columns on `ClientEntity` instead of a separate table:** There is a 1:1 relationship, the freeze state is a property of the account, and the existing `AbstractAuditingEntity` audit trail already captures who/when the entity was last modified. A separate table adds joins without benefit.

### Pattern 2: Platform Freeze as Singleton Table

The platform freeze state follows the same singleton pattern as `platform_credit_balance` (id=1 seed row, SELECT FOR UPDATE, no application-level inserts). The singleton row already exists once seeded by Flyway.

```sql
-- V12__account_freeze.sql (partial)
CREATE TABLE main.platform_freeze_state (
    id                  BIGINT PRIMARY KEY,
    frozen              BOOLEAN NOT NULL DEFAULT FALSE,
    frozen_at           TIMESTAMP,
    freeze_reason       TEXT,
    shortfall_amount    BIGINT,                  -- PFLAT-03: unrecovered shortfall
    freeze_resolved_at  TIMESTAMP,
    freeze_resolution   TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50),
    created_date        TIMESTAMP,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMP,
    request_id          VARCHAR(100),
    session_id          TEXT
);

INSERT INTO main.platform_freeze_state (id, frozen, status, created_date)
VALUES (1, FALSE, 'ACTIVE', NOW());
```

The `PlatformFreezeService` uses `findForUpdate()` (same `@Lock(PESSIMISTIC_WRITE)` pattern as `PlatformCreditBalanceRepository`) to serialize freeze/unfreeze transitions.

### Pattern 3: SUSPENDED Status in SendRequestStatus

`SendRequestStatus` currently: `ACCEPTED, SUBMITTED, COMPLETED, FAILED, FINALIZED, FAIL_FINALIZED, CANCELLED`.

Add `SUSPENDED` between `ACCEPTED` and `SUBMITTED`. A suspended request is:
- Still has `scheduleTime` set (the schedule is retained — CFREEZE-02, PFLAT-02)
- Returns to `ACCEPTED` on unfreeze (auto-resume — CFREEZE-04, PFLAT-05)
- Cannot be dispatched by `SmsSchedulerService` while in `SUSPENDED` state
- Can still be cancelled by the client (becomes `CANCELLED` — releases reserved credits)

The `SmsSchedulerService.dispatchScheduledMessages()` already filters on `ACCEPTED` status via `findDueScheduledRequests()` and `findPendingImmediateRequests()`. Adding `SUSPENDED` requires no change there — suspended requests are simply invisible to the scheduler.

### Pattern 4: Freeze Check in CreditReservationService.reserve()

`CreditReservationService.reserve()` is the single serialization point for all credit consumption. The freeze check belongs here before the balance check:

```java
// Check 1: Client-level freeze
ClientEntity client = clientRepository.findById(clientId)
    .orElseThrow(() -> new ResourceNotFoundException(...));
if (client.isFrozen()) {
    throw new AccountFrozenException("Client account is frozen", clientId);
}

// Check 2: Platform-level freeze
PlatformFreezeState pfState = platformFreezeRepository.findState()
    .orElseThrow(() -> ...);
if (pfState.isFrozen()) {
    throw new PlatformFrozenException("Platform is frozen");
}

// Existing balance check follows
ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)...
```

**Import concern:** `CreditReservationService` is in `gateway.billing.service`. `ClientRepository` is in `gateway.account.repo`. Per ARCHITECTURE.md, `service` cannot import `repo` from another domain. Resolution: inject `ClientService` (from `gateway.account.service`) and add a `isFrozen(Long clientId)` read-only method to it; or add a thin `ClientFreezeService` that `CreditReservationService` calls. `CreditReservationService` calling a service from `account` module is permitted — `service` may call other `service` beans, the prohibition is only on direct `repo` imports across module boundaries.

### Pattern 5: Bulk SMS Suspension on Freeze

When a client is frozen, all their `ACCEPTED` scheduled SMS (with `scheduleTime IS NOT NULL`) must move to `SUSPENDED`. Use `@Modifying @Query`:

```java
// SendRequestRepository
@Modifying
@Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED " +
       "WHERE s.clientId = :clientId " +
       "AND s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
       "AND s.scheduleTime IS NOT NULL")
int suspendScheduledForClient(@Param("clientId") Long clientId);

@Modifying
@Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
       "WHERE s.clientId = :clientId " +
       "AND s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED")
int resumeScheduledForClient(@Param("clientId") Long clientId);
```

For platform freeze, the same pattern without the `clientId` filter:

```java
@Modifying
@Query("UPDATE SendRequest s SET s.sendStatus = ...SUSPENDED " +
       "WHERE s.sendStatus = ...ACCEPTED AND s.scheduleTime IS NOT NULL")
int suspendAllScheduled();

@Modifying
@Query("UPDATE SendRequest s SET s.sendStatus = ...ACCEPTED " +
       "WHERE s.sendStatus = ...SUSPENDED")
int resumeAllScheduled();
```

**Key requirement:** These bulk updates must execute in the same transaction as the freeze state change. Both `ClientFreezeService` and `PlatformFreezeService` must be `@Transactional` and call the repository update methods directly (same transaction).

### Anti-Patterns to Avoid

- **Using EntityStatus.INACTIVE for frozen accounts:** INACTIVE is an account deactivation signal, not a billing hold. The existing `ClientRepository.findAllWithBalance()` and other queries already treat INACTIVE as "soft-deleted." A separate `frozen` boolean is unambiguous.
- **Adding SUSPENDED to SmsSchedulerService filter:** The scheduler already ignores non-ACCEPTED rows. Adding explicit exclusion of SUSPENDED adds no value and creates a maintenance coupling.
- **Cross-domain repo import in CreditReservationService:** Do not import `ClientRepository` from `gateway.account.repo` into `gateway.billing.service`. Use a service method (see Pattern 4 above).
- **Separate freeze history table:** The `AbstractAuditingEntity` audit columns (createdDate, lastModifiedDate, lastModifiedBy) plus the domain audit log (`AuditEventType`) provide sufficient freeze history for v1. A separate history table adds no requirement coverage.
- **Platform freeze check without the singleton lock:** Do not add a simple boolean field to `PlatformCreditBalance`. Platform freeze is a separate concern from platform balance and deserves its own singleton entity with its own locking.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bulk status update for many SendRequest rows | Loop + individual saves | `@Modifying @Query` JPQL UPDATE | One SQL UPDATE vs N individual saves; essential for platform freeze which affects all clients |
| Freeze-state read-under-lock | Manual lock logic | `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(timeout)` | Existing pattern in `ClientCreditBalanceRepository` and `PlatformCreditBalanceRepository` — copy exactly |
| New exception types | Generic RuntimeException | Extend `ApplicationException` with an `ErrorCode` enum | Required for `ApiAdvice` to handle with the correct HTTP status and error_code |
| Admin endpoint security | Manual role check | Class-level `@PreAuthorize("hasRole('ADMIN')")` | Same as `AdminPlatformCreditResource` decision (19-03) |
| Audit trail for freeze/unfreeze | Custom audit table | `DomainAuditEvent` + `AuditEventType` enum constants | Existing audit infrastructure handles persistence and querying |

**Key insight:** The freeze lifecycle is a state machine on existing entities. Every tool needed (lock patterns, bulk updates, exception hierarchy, audit events, security wiring) is already present in the codebase. The plan is additive, not structural.

---

## Common Pitfalls

### Pitfall 1: In-flight sends must complete (CFREEZE-05, PFLAT-04)
**What goes wrong:** Freeze logic that tries to cancel or interrupt `SUBMITTED` sends (those already handed to Nexah). The DR callback (`DrCallbackService`) and final booking (`SmsFinalisedBillingListener`) must continue to function on frozen accounts.
**Why it happens:** Naive implementation suspends everything with ACCEPTED or SUBMITTED status.
**How to avoid:** The bulk suspend query must filter `AND s.scheduleTime IS NOT NULL` AND `AND s.sendStatus = ACCEPTED` — this leaves SUBMITTED rows untouched. The DR callback path has no freeze check — it just processes whatever Nexah reports back.
**Warning signs:** If the suspend query touches rows with `sendStatus = SUBMITTED` or `scheduleTime IS NULL`, in-flight sends will be disrupted.

### Pitfall 2: Immediate (non-scheduled) sends in ACCEPTED state
**What goes wrong:** `SmsSchedulerService` also dispatches non-scheduled immediate sends via `findPendingImmediateRequests()` (which finds `sendStatus=ACCEPTED AND scheduleTime IS NULL`). If the client is frozen, these could theoretically be dispatched between the freeze commit and the next scheduler run.
**Why it happens:** Freeze commits and scheduler dispatch are not in the same transaction — there is a window.
**How to avoid:** The freeze check in `CreditReservationService.reserve()` prevents new immediate sends from being accepted. Sends already in ACCEPTED state with no scheduleTime that were accepted BEFORE the freeze will be dispatched on the next scheduler cycle — this is acceptable because credits were already reserved and the freeze check cannot retroactively un-accept them without releasing credits (which would change billing semantics). CFREEZE-02 explicitly applies only to "pending scheduled SMS" — not to immediate sends in queue.
**Warning signs:** Requirements CFREEZE-02 and PFLAT-02 say "scheduled SMS are suspended" — not "all ACCEPTED SMS."

### Pitfall 3: SUSPENDED rows and SmsSchedulerService
**What goes wrong:** The scheduler's `findDueScheduledRequests()` filters `sendStatus = ACCEPTED`. After adding SUSPENDED, resumed sends transition back to ACCEPTED. A resumed send that still has a past schedule_time will be picked up immediately on the next scheduler cycle, which is correct. But if the bulk resume query transitions ALL suspended rows (including those whose schedule_time is in the past), they will all be dispatched together. This is the correct behavior — the schedule is "retained" per CFREEZE-02 semantics.
**How to avoid:** No special handling needed. The scheduler's existing query works correctly with SUSPENDED as a status.
**Warning signs:** Adding a `scheduleTime > now` filter to the resume query would silently discard past-scheduled sends.

### Pitfall 4: Lock ordering with freeze state
**What goes wrong:** `ClientFreezeService.freeze(clientId)` acquires a lock on `ClientEntity`, then calls `SendRequestRepository` bulk update — no additional lock needed for the SendRequest rows (JPQL UPDATE locks the rows it touches). However, `CreditReservationService.reserve()` acquires `ClientCreditBalance` lock and also needs to check freeze state. If freeze state is checked without a lock, there is a race between freeze-commit and reserve.
**Why it happens:** Optimistic read of freeze state checked outside a lock.
**How to avoid:** The freeze check in `reserve()` uses a regular `findById` (non-locking read). This is acceptable because: (1) freeze is set atomically in its own transaction, (2) a brief window where a request slips through is not dangerous — the credit was already reserved, it just can't send anyway (scheduler won't dispatch while frozen). The correctness guarantee is that NO NEW reservations are accepted after the freeze is committed, not that zero reservations exist at the exact moment of freeze. This is the same eventual-consistency guarantee as the circuit-breaker check in SmsService.

### Pitfall 5: ApiAdvice handlers for new exceptions
**What goes wrong:** New exception types (`AccountFrozenException`, `PlatformFrozenException`) fall through to the `defaultErrorHandler` returning HTTP 500 instead of the intended status code.
**How to avoid:** Add explicit `@ExceptionHandler` methods to `ApiAdvice` for each new exception. `AccountFrozenException` → HTTP 403 (ACCOUNT_FROZEN). `PlatformFrozenException` → HTTP 503 (PLATFORM_FROZEN). These are client-observable codes consistent with the existing patterns.

### Pitfall 6: AppEndpoints SECURED_MAPPINGS capacity
**What goes wrong:** `Map.ofEntries()` already has 14 entries. Adding two new paths (freeze + platform freeze) brings it to 16. This is safe — `Map.ofEntries()` has no cap (the original `Map.of()` cap of 10 was resolved in Phase 10). No migration needed.
**How to avoid:** Just add new `Map.entry()` calls. Verify count doesn't exceed `Map.of()` limit if someone reverts to `Map.of()` — but the code already uses `Map.ofEntries()`.

---

## Code Examples

Verified patterns from existing codebase:

### Pessimistic lock with timeout (existing pattern)
```java
// Source: ClientCreditBalanceRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM ClientCreditBalance b WHERE b.clientId = :clientId")
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
Optional<ClientCreditBalance> findByClientIdForUpdate(@Param("clientId") Long clientId);
```

Freeze service lock for client follows the same pattern on `ClientRepository`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM ClientEntity c WHERE c.id = :id")
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
Optional<ClientEntity> findByIdForUpdate(@Param("id") Long id);
```

### ApplicationException subclass pattern (existing)
```java
// Source: InsufficientBalanceException.java
public class AccountFrozenException extends ApplicationException {
    private final Long clientId;

    public AccountFrozenException(String message, Long clientId) {
        super(message, ClientError.ACCOUNT_FROZEN);  // new ClientError constant
        this.clientId = clientId;
    }

    public Long getClientId() { return clientId; }
}
```

### Bulk JPQL update pattern (existing @Modifying usage from SendRequestRepository.java)
```java
// Source: SendRequestRepository.java — deleteAllByIdIn shows @Modifying pattern
@Modifying
@Query("DELETE FROM SendRequest s WHERE s.id IN :ids")
void deleteAllByIdIn(@Param("ids") List<Long> ids);

// New bulk suspend/resume methods follow same pattern
@Modifying
@Query("UPDATE SendRequest s SET s.sendStatus = ...SUSPENDED " +
       "WHERE s.clientId = :clientId AND s.sendStatus = ...ACCEPTED " +
       "AND s.scheduleTime IS NOT NULL")
int suspendScheduledForClient(@Param("clientId") Long clientId);
```

### DomainAuditEvent publishing (existing pattern)
```java
// Source: ClientService.java, TopupService.java
eventPublisher.publishEvent(new DomainAuditEvent(
    AuditEventType.CLIENT_ACCOUNT_FROZEN,  // new constant
    clientId,
    resolveAdminActor(),
    "Client frozen: reason=" + reason
));
```

### Singleton pattern for platform state (existing)
```java
// Source: PlatformCreditBalanceRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM PlatformCreditBalance b")
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
Optional<PlatformCreditBalance> findForUpdate();

// New PlatformFreezeStateRepository mirrors exactly:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PlatformFreezeState p")
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
Optional<PlatformFreezeState> findForUpdate();
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Freeze not implemented | Phase 20 introduces freeze lifecycle | All reservation code gains two new guard checks |
| `SendRequestStatus` has 7 values | Phase 20 adds `SUSPENDED` (8th value) | Scheduler, status queries, cancellation — all continue to work; only `findDueScheduledRequests` and `findPendingImmediateRequests` are implicitly correct since they already filter on `ACCEPTED` |
| `AuditEventType` has 12 constants | Phase 20 adds 4 new constants | No structural change |
| `AppEndpoints.SECURED_MAPPINGS` has 14 entries | Phase 20 adds 2 new admin paths | 16 entries, `Map.ofEntries()` has no cap |
| `ClientEntity` has name + status fields | Phase 20 adds 5 freeze columns | V12 ALTER TABLE migration |

---

## Key Design Decisions Needed

These items the planner must resolve:

1. **Which module owns `ClientFreezeService`?**
   Recommendation: `gateway.account.service` — it operates on `ClientEntity`, which is in the account module. The freeze check in `CreditReservationService` (billing) calls `clientAccountFreezeService.isFrozen(clientId)` — a read-only call across module boundary at the service level (permitted by architecture).

2. **Which module owns `PlatformFreezeService`?**
   Recommendation: `gateway.billing.service` — platform freeze is a billing infrastructure concern, same as `PlatformCreditService`. It shares the `billing` package and can be called from `CreditReservationService` directly without crossing module boundaries.

3. **HTTP status for AccountFrozenException?**
   Recommendation: HTTP 403 (Forbidden) with error_code `ACCOUNT_FROZEN`. Rationale: the client's credentials are valid, but the account is not permitted to perform the operation. This is semantically distinct from 402 Payment Required (not widely used) and aligns with how API consumers will handle it programmatically.

4. **HTTP status for PlatformFrozenException?**
   Recommendation: HTTP 503 (Service Unavailable) with error_code `PLATFORM_FROZEN`. Rationale: the platform cannot serve the request due to an infrastructure freeze, not a client-specific problem. Clients should retry later.

5. **Admin unfreeze endpoint path convention?**
   Recommendation: `PUT /api/admin/clients/{clientId}/freeze` (freeze), `DELETE /api/admin/clients/{clientId}/freeze` or `PUT /api/admin/clients/{clientId}/unfreeze` (unfreeze). The PUT/DELETE semantic is cleaner than two POST endpoints.
   Platform: `POST /api/admin/platform/freeze` and `DELETE /api/admin/platform/freeze` (or `PUT /api/admin/platform/unfreeze`).

---

## Open Questions

1. **Freeze trigger — who calls freeze?**
   - What's unclear: The requirements describe freeze on "shortfall" and by admin action. Phase 20 seems to be the infrastructure only (the trigger from Phase 19/21 is separate). The planner should scope the freeze trigger explicitly: Phase 20 likely implements admin-initiated freeze/unfreeze plus the reservation guard; automatic freeze-on-shortfall may be Phase 21.
   - Recommendation: Scope Phase 20 to the freeze infrastructure and admin controls. Mark shortfall-triggered freeze as a Phase 21 concern unless the requirements clearly scope it here.

2. **Can a client cancel a SUSPENDED scheduled SMS?**
   - What we know: CFREEZE-04 says suspended SMS resume on unfreeze. CFREEZE-02 says they are "not cancelled." The client's cancel endpoint (`DELETE /v1/sms/{sendRequestId}`) currently checks `sendStatus == ACCEPTED` — it would reject a SUSPENDED request.
   - What's unclear: Should clients be allowed to cancel SUSPENDED scheduled SMS? Allowing cancel avoids credit lock-up on frozen accounts.
   - Recommendation: Allow cancel of SUSPENDED scheduled SMS — it releases reserved credits cleanly. Update `SmsService.cancelScheduled()` to also permit `sendStatus == SUSPENDED`.

3. **Should freeze state be visible to clients via the balance/status API?**
   - What's unclear: The requirements mention admin controls, but not whether clients see a "frozen" indicator in their API responses.
   - Recommendation: Out of scope for Phase 20. The HTTP 403 response on send attempt is sufficient signal. Document this decision in the plan.

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection: `CreditReservationService.java`, `SmsService.java`, `SmsSchedulerService.java`, `ClientEntity.java`, `SendRequest.java`, `SendRequestStatus.java`, `SendRequestRepository.java`, `PlatformCreditBalance.java`, `PlatformCreditService.java`, `PlatformCreditBalanceRepository.java`, `ClientCreditBalanceRepository.java`, `ApiAdvice.java`, `AppEndpoints.java`, `AuditEventType.java`, `AbstractAuditingEntity.java`, `ARCHITECTURE.md`
- Flyway migrations: V3 (credit ledger), V5 (send_request), V11 (platform_credit_account)
- Phase 19 decisions in STATE.md: singleton pattern, lock ordering, ADMIN authority pattern

### Secondary (MEDIUM confidence)
- Spring Data JPA `@Modifying @Query` bulk update semantics — verified from code patterns in `SendRequestRepository.deleteAllByIdIn()`
- `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(timeout)` — verified from two existing repository implementations

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all tools are existing, verified by direct code inspection
- Architecture: HIGH — patterns traced from two comparable Phase 19 implementations
- Pitfalls: HIGH — derived from existing constraints in code (scheduler query filters, in-flight completion requirements, import rules)
- Open questions: MEDIUM — requirements are clear but implementation boundary between Phase 20 and 21 needs planner decision

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable stack — 30 days)
