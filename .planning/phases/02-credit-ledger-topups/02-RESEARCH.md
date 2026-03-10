# Phase 2: Credit Ledger & Top-Ups - Research

**Researched:** 2026-03-10
**Domain:** Financial ledger design, PostgreSQL locking, Spring Data JPA, top-up state machine
**Confidence:** HIGH (codebase verified + official docs) / MEDIUM (performance estimates)

---

## Summary

Phase 2 implements the financial core: an append-only credit ledger, balance derivation, and the atomic reservation guarantee. The architecture is ledger-first — balance is never stored as a mutable column; it is derived by `SUM(amount)` over ledger entries. Top-ups follow a two-state lifecycle: `PENDING_APPROVAL` on submission, `APPROVED` or `REJECTED` after admin action.

The single most critical engineering decision is the atomic reservation strategy. Two concurrent send requests for the same client must not both succeed if together they would exceed the available balance. For a single PostgreSQL instance with Spring JPA, the correct approach is `SELECT FOR UPDATE` on a dedicated per-client balance-lock row, expressed via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository method. This is simpler, safer, and better understood than advisory locks or optimistic retry loops for a strict "balance never goes negative" guarantee.

The `balance_after` field on each ledger entry is stored at write time (computed from prior `SUM + this amount`), not re-derived on read. This enables the ledger history response to return `balance_after` without an expensive running-total query, while keeping balance authoritative as `SUM(amount)` from the canonical `credit_balance_lock` row.

**Primary recommendation:** Use a `client_credit_balance` lock row (one row per client, holding the current balance) protected by `SELECT FOR UPDATE`. All balance-mutating operations lock this row first, compute the new balance, write the ledger entry with `balance_after`, then update the lock row's `balance` column. Balance reads outside a reservation use `SELECT balance FROM client_credit_balance WHERE client_id = ?` — one indexed lookup, no SUM needed at read time.

---

## Standard Stack

All libraries are already present in `pom.xml`. No new dependencies are required for Phase 2.

### Core (already in pom.xml)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| spring-boot-starter-data-jpa | 3.5.11 managed | JPA entities, repositories, `@Lock` | Already used |
| postgresql | managed | JDBC driver, `SELECT FOR UPDATE` support | Already used |
| flyway-core | managed | DB schema migrations | Already used |
| hypersistence-utils-hibernate-63 | present | `@Tsid` primary key generation | Already used in `BaseEntity` |
| lombok | managed | `@SuperBuilder`, `@NoArgsConstructor` | Already used in all entities |
| spring-boot-starter-validation | managed | `@Valid`, `@NotNull`, `@Positive` on request DTOs | Already used |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Lock row + `SELECT FOR UPDATE` | Advisory locks (`pg_advisory_xact_lock`) | Advisory locks require the application to assign stable numeric IDs per client and add `pg_try_advisory_xact_lock` calls. `SELECT FOR UPDATE` integrates cleanly with JPA `@Lock` and is more maintainable. Use advisory locks only if locking outside an entity lifecycle is needed. |
| Lock row + `SELECT FOR UPDATE` | Optimistic locking (`@Version` + retry) | Optimistic locking fires `OptimisticLockException` on conflict and requires a retry loop. Under contention, clients may see elevated latency. For a hard "never negative" guarantee, retries can still overspend if the retry succeeds after another concurrent debit. Pessimistic is the correct choice here. |
| Denormalized `balance` lock row | Pure `SUM(amount)` on every balance read | Pure SUM is correct but requires scanning all ledger rows per client on every balance query. With a composite index `(client_id, id)` it is fast for small ledgers but grows linearly with history. The lock row gives O(1) balance reads at the cost of one extra write per ledger entry. |

**Installation:** No new Maven dependencies needed.

---

## Architecture Patterns

### Recommended Package Structure

New code lives entirely within the existing `client` module, adding sub-packages alongside the existing `api`, `service`, `repo`, `contract`, `config` packages:

```
src/main/java/com/softropic/sendam/client/
├── api/
│   ├── AdminClientResource.java          EXISTING — add GET /api/admin/clients
│   ├── CreditResource.java               NEW — GET /v1/credits/balance, GET /v1/credits/ledger
│   └── TopupResource.java                NEW — POST /v1/credits/topups, GET /v1/credits/topups/{id}
│   └── AdminTopupResource.java           NEW — PUT /api/admin/topups/{id}/approve, PUT /api/admin/topups/{id}/reject
├── service/
│   ├── ClientService.java                EXISTING — add getAllClients() for ADMIN-03
│   ├── ApiKeyService.java                EXISTING
│   ├── CreditService.java                NEW — getBalance, getLedgerHistory
│   └── TopupService.java                 NEW — createTopup, approve, reject
├── repo/
│   ├── ClientEntity.java                 EXISTING
│   ├── ClientRepository.java             EXISTING
│   ├── ClientApiKeyEntity.java           EXISTING
│   ├── ClientApiKeyRepository.java       EXISTING
│   ├── CreditLedgerEntry.java            NEW — @Entity for credit_ledger_entry table
│   ├── CreditLedgerRepository.java       NEW — JpaRepository + custom @Lock query
│   ├── ClientCreditBalance.java          NEW — @Entity for client_credit_balance table (lock row)
│   └── ClientCreditBalanceRepository.java NEW — @Lock(PESSIMISTIC_WRITE) findByClientId
│   ├── TopupRequestEntity.java           NEW — @Entity for topup_request table
│   └── TopupRequestRepository.java       NEW
└── contract/
    ├── BalanceResponse.java              NEW — record for GET /v1/credits/balance response
    ├── LedgerHistoryResponse.java        NEW — record for GET /v1/credits/ledger response
    ├── LedgerEntryDto.java               NEW — individual ledger entry in response
    ├── CreateTopupRequest.java           NEW — record for POST /v1/credits/topups body
    ├── CreateTopupResponse.java          NEW — record for POST response
    ├── TopupStatusResponse.java          NEW — record for GET /v1/credits/topups/{id}
    ├── TopupStatus.java                  NEW — enum PENDING_APPROVAL, APPROVED, REJECTED
    ├── LedgerEntryType.java              NEW — enum TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND
    └── AdminClientDto.java               NEW — client + balance for ADMIN-03 list
```

### Pattern 1: Balance Lock Row with SELECT FOR UPDATE

Each client has exactly one row in `client_credit_balance`. All balance-mutating operations acquire an exclusive lock on this row before computing or writing. This makes the lock row the single serialization point for a given client.

**Read flow (balance query — no lock):**
```
GET /v1/credits/balance
→ CreditResource.getBalance()
→ CreditService.getBalance(clientId)
→ clientCreditBalanceRepository.findByClientId(clientId)   ← simple SELECT, no lock
→ return balance
```

**Write flow (reservation — with lock):**
```
POST /v1/sms/send (Phase 3)
→ CreditService.reserveCredits(clientId, amount)
→ @Transactional
   1. clientCreditBalanceRepository.findByClientIdForUpdate(clientId)  ← SELECT FOR UPDATE
   2. check balance >= amount  →  if not, throw InsufficientBalanceException
   3. newBalance = balance - amount
   4. save CreditLedgerEntry{type=SMS_RESERVATION, amount=-amount, balance_after=newBalance}
   5. lockRow.setBalance(newBalance); save(lockRow)
   6. commit
```

**Why this is correct:** Steps 1-6 happen within a single `@Transactional` method. The `SELECT FOR UPDATE` at step 1 blocks any concurrent transaction that tries to reserve or top up for the same client until the current transaction commits. PostgreSQL guarantees serialized execution per lock row. Balance never goes negative because the check at step 2 is inside the exclusive lock.

```java
// Source: logicbig.com tutorial + vladmihalcea.com Spring Data JPA locking, verified
// In ClientCreditBalanceRepository:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM ClientCreditBalance b WHERE b.clientId = :clientId")
Optional<ClientCreditBalance> findByClientIdForUpdate(@Param("clientId") Long clientId);

// Hibernate generates for PostgreSQL:
// SELECT ... FROM client_credit_balance WHERE client_id = ? FOR UPDATE
```

### Pattern 2: Ledger Entry Append

Ledger entries are immutable once written. The `balance_after` field is computed at write time from the lock row's pre-update balance plus the entry's `amount`.

```java
// Source: codebase patterns (AbstractAuditingEntity, @Tsid BaseEntity)
// In CreditService:
@Transactional
public void applyLedgerEntry(Long clientId, LedgerEntryType type,
                              long amount, String reference) {
    // amount: positive for credits, negative for debits/reservations
    ClientCreditBalance lockRow = balanceRepo.findByClientIdForUpdate(clientId)
        .orElseThrow(() -> new ResourceNotFoundException("No balance row for client " + clientId));

    long balanceBefore = lockRow.getBalance();
    long newBalance = balanceBefore + amount;

    if (newBalance < 0) {
        throw new InsufficientBalanceException(clientId, balanceBefore, Math.abs(amount));
    }

    CreditLedgerEntry entry = CreditLedgerEntry.builder()
        .clientId(clientId)
        .entryType(type)
        .amount(amount)                  // signed: -3 for SMS_RESERVATION of 3 credits
        .balanceAfter(newBalance)
        .reference(reference)
        .build();
    ledgerRepo.save(entry);

    lockRow.setBalance(newBalance);
    balanceRepo.save(lockRow);
}
```

### Pattern 3: Top-Up State Machine (Enum-based, No Spring State Machine)

The top-up lifecycle has only 3 states and 2 transitions. Spring State Machine is overkill. Use a `TopupStatus` enum and enforce transitions in `TopupService`.

```
PENDING_APPROVAL  →  APPROVED   (admin approves)
PENDING_APPROVAL  →  REJECTED   (admin rejects)
```

No other transitions are valid. `TopupService.approve()` throws if `status != PENDING_APPROVAL`.

**Approve flow:**
1. Load `TopupRequestEntity` by `topup_id` — verify `status == PENDING_APPROVAL`
2. Within the same `@Transactional`, call `applyLedgerEntry(clientId, TOPUP_APPROVED, +amount, topup_id)`
3. Set `topupRequest.status = APPROVED`, `topupRequest.approvedAt = now()`
4. Save both in same transaction

**Reject flow:**
1. Load `TopupRequestEntity` by `topup_id` — verify `status == PENDING_APPROVAL`
2. No ledger entry needed for rejection (the TOPUP_PENDING entry was informational)
3. Set `topupRequest.status = REJECTED`, `topupRequest.rejectedAt = now()`
4. Save

**Note on TOPUP_PENDING ledger entry:** The v8 contract says "Ledger entry created: TOPUP_PENDING" when a top-up is submitted. This entry has amount = 0 (informational — it documents that a pending top-up exists) or is simply a record with `type=TOPUP_PENDING` and `amount=0`. It does not affect the balance. When the admin approves, a second entry with `type=TOPUP_APPROVED` and `amount=+N` is created and the lock row balance is incremented. The ledger history endpoint returns both entry types.

### Pattern 4: Admin View Clients (ADMIN-03)

`GET /api/admin/clients` returns all clients with their current balances. Use a `@Query` that joins `client_account` and `client_credit_balance`:

```java
// In ClientRepository or a dedicated query:
@Query("SELECT new com.softropic.sendam.client.contract.AdminClientDto(" +
       "c.id, c.name, c.status, b.balance) " +
       "FROM ClientEntity c LEFT JOIN ClientCreditBalance b ON b.clientId = c.id")
List<AdminClientDto> findAllWithBalance();
```

No pagination required by v8 contract for this endpoint (admin use, bounded client count for v1).

### Pattern 5: Ledger History Pagination

The v8 contract does not specify pagination parameters for `GET /v1/credits/ledger`. The response shape is `{"entries": [...]}`. For v1, use **offset pagination** with `Pageable` (Spring Data standard). Keyset pagination is architecturally better for large histories but adds complexity not justified by the contract's current shape.

Default page size: 50 entries, max 200. Sort by `created_date DESC` (newest first).

```java
// CreditLedgerRepository:
Page<CreditLedgerEntry> findByClientIdOrderByCreatedDateDesc(Long clientId, Pageable pageable);
```

Query parameters: `?page=0&size=50` (add in controller; not in v8 contract body but acceptable as standard Spring pagination).

### Anti-Patterns to Avoid

- **Do not store balance as a mutable column on `ClientEntity`.** It drifts. The `client_credit_balance` lock row exists solely to enable `SELECT FOR UPDATE` — it is the ledger aggregate, not a denormalized shortcut on the client table.
- **Do not call `applyLedgerEntry` outside a `@Transactional` method.** The lock row update and ledger entry write must be in the same transaction or you get an inconsistent state.
- **Do not use `@Lock` on a `findAll` or a query that returns multiple clients.** Lock only the single client's balance row being reserved.
- **Do not use `LockModeType.PESSIMISTIC_READ` (FOR SHARE).** Balance reservation requires exclusive write locks — `PESSIMISTIC_READ` only blocks writers from other writers, not readers, which is insufficient.
- **Do not set a very long pessimistic lock timeout.** A stuck transaction holding a lock blocks all reservations for that client. Configure `jakarta.persistence.lock.timeout` on the `findByClientIdForUpdate` query hint to a short value (e.g., 2000ms).
- **Do not write retry logic for pessimistic lock failures.** If the lock cannot be acquired within the timeout, return 503 to the caller — the request should be retried at the HTTP level.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TSID primary keys | `@GeneratedValue(AUTO)` or `UUID` | `@Tsid` from `hypersistence-utils` (already in `BaseEntity`) | All existing entities use it; consistent pattern |
| Auditing columns (`created_by`, `created_date`, etc.) | Manual field setters | Extend `AbstractAuditingEntity` (existing `@MappedSuperclass`) | All audit columns handled by `AuditingEntityListener` |
| Lock row `SELECT FOR UPDATE` | Raw `EntityManager.createNativeQuery` with `FOR UPDATE` | `@Lock(LockModeType.PESSIMISTIC_WRITE)` on Spring Data repository method | Maps directly to `FOR UPDATE` in PostgreSQL; integrates with Spring TX |
| Paginated ledger history | Custom LIMIT/OFFSET query | `Page<CreditLedgerEntry> findBy...(Pageable)` — Spring Data handles LIMIT/OFFSET | Standard pattern already used in codebase |
| Topup ID format `"top_12345"` | String generation service | TSID `Long` formatted as `"top_" + id.toString()` at serialization time | Consistent with API key pattern; no custom ID service needed |

**Key insight:** The `AbstractAuditingEntity` already provides `created_date`, `request_id`, and all audit columns. Every new entity for Phase 2 should extend it. The `created_date` field (type `Instant`) serves as the ledger entry timestamp — no separate `timestamp` column is needed.

---

## Common Pitfalls

### Pitfall 1: Pessimistic Lock Not Acquired Due to Missing `@Transactional`
**What goes wrong:** `clientCreditBalanceRepository.findByClientIdForUpdate(clientId)` is called, but no active transaction exists. JPA throws `TransactionRequiredException` at runtime.
**Why it happens:** `@Lock` requires an active transaction. If the service method lacks `@Transactional`, the lock is not applied.
**How to avoid:** Always annotate the service method that calls the lock query with `@Transactional`. The entire reserve-check-write sequence must be one transaction.
**Warning signs:** `javax.persistence.TransactionRequiredException` in logs; no `FOR UPDATE` in Hibernate SQL output.

### Pitfall 2: Lock Row Missing for New Clients
**What goes wrong:** `findByClientIdForUpdate(clientId)` returns `Optional.empty()` for a newly created client. Code that calls `.orElseThrow()` breaks the first top-up approval.
**Why it happens:** The `client_credit_balance` row must be created when the client is created, not lazily.
**How to avoid:** In `ClientService.createClient()`, after saving `ClientEntity`, also insert a `ClientCreditBalance` row with `balance = 0`. Use the same Flyway migration that references `client_account` with a FK.
**Warning signs:** `ResourceNotFoundException` on first `approve()` call for a new client.

### Pitfall 3: TOPUP_PENDING Ledger Entry Inflates the Balance
**What goes wrong:** The balance SUM includes the TOPUP_PENDING entry's positive amount, making the balance appear higher than it should before admin approval.
**Why it happens:** If TOPUP_PENDING entries carry `amount > 0`, they incorrectly inflate the balance before approval.
**How to avoid:** TOPUP_PENDING entries must have `amount = 0` (informational record only). The balance is only changed by TOPUP_APPROVED entries. Alternatively, exclude `TOPUP_PENDING` entries from the SUM — but the lock-row approach makes this moot since the lock row's balance is never modified by TOPUP_PENDING.
**Warning signs:** Client balance shows a pending top-up amount before admin approval.

### Pitfall 4: Concurrent Top-Up Approval Race Condition
**What goes wrong:** Admin approves the same top-up request twice (double-click, network retry). Both approval requests succeed, crediting the client twice.
**Why it happens:** The approval check `status == PENDING_APPROVAL` is not inside the exclusive lock.
**How to avoid:** Within the approval transaction, load the `TopupRequestEntity` for update (`@Lock(PESSIMISTIC_WRITE)`), check status, and update it atomically. The status transition `PENDING_APPROVAL → APPROVED` is idempotent only if the check-and-set is atomic.
**Warning signs:** Ledger contains two `TOPUP_APPROVED` entries for the same `topup_id`.

### Pitfall 5: `balance_after` Computed from Stale Balance
**What goes wrong:** Two concurrent transactions read the balance row, both compute `balance_after = 100 - 3 = 97`, and write two ledger entries both claiming `balance_after = 97`. The actual balance becomes 94, but ledger history shows 97 twice.
**Why it happens:** Without `SELECT FOR UPDATE`, both transactions read the same balance before either commits.
**How to avoid:** The `SELECT FOR UPDATE` on the balance lock row prevents this — only one transaction can compute and write at a time. Never compute `balance_after` from a non-locked read.
**Warning signs:** Ledger history has duplicate `balance_after` values; actual balance diverges from history.

### Pitfall 6: `EntityStatus` Misuse for TopupStatus
**What goes wrong:** Using the existing `EntityStatus` enum (ACTIVE / INACTIVE / DELETED) to represent top-up lifecycle states because `TopupRequestEntity` extends `AbstractAuditingEntity` which already has a `status` field of type `EntityStatus`.
**Why it happens:** `AbstractAuditingEntity.status` is present on all entities. It is tempting to reuse it for domain state.
**How to avoid:** Add a separate `topupStatus` column of type `TopupStatus` enum (PENDING_APPROVAL / APPROVED / REJECTED) on `TopupRequestEntity`, independent of the inherited `status` field. The inherited `status` can be `ACTIVE` always (the topup record is never deleted). The domain lifecycle is tracked in `topupStatus`.
**Warning signs:** Code tries to set `entity.setStatus(EntityStatus.APPROVED)` — compile error because `APPROVED` is not in `EntityStatus`.

### Pitfall 7: Non-Unique `transaction_id` Constraint (TOPUP-03)
**What goes wrong:** Same `transaction_id` submitted twice by the same client creates two top-up requests.
**Why it happens:** No unique constraint on `(client_id, transaction_id)`.
**How to avoid:** Add a `UNIQUE (client_id, transaction_id)` constraint in the Flyway migration for `topup_request`. `TopupService.createTopup()` catches `DataIntegrityViolationException` and translates to HTTP 409 with `DUPLICATE_TRANSACTION_ID` error code.
**Warning signs:** Duplicate `transaction_id` rows in `topup_request` table.

---

## Code Examples

### Flyway Migration Schema

```sql
-- V3__credit_ledger.sql
-- Lock row: one row per client, holds current balance for SELECT FOR UPDATE
CREATE TABLE main.client_credit_balance (
    id          BIGINT PRIMARY KEY,
    client_id   BIGINT NOT NULL UNIQUE REFERENCES main.client_account(id),
    balance     BIGINT NOT NULL DEFAULT 0,  -- unit: SMS segments (integer, no decimals)
    version     BIGINT NOT NULL DEFAULT 0   -- optimistic lock version (unused for balance, for future use)
);
CREATE INDEX idx_client_credit_balance_client ON main.client_credit_balance(client_id);

-- Ledger: append-only, one entry per credit event
CREATE TABLE main.credit_ledger_entry (
    id            BIGINT PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES main.client_account(id),
    entry_type    VARCHAR(30) NOT NULL,   -- TOPUP_PENDING, TOPUP_APPROVED, SMS_RESERVATION, SMS_DEBIT, SMS_REFUND
    amount        BIGINT NOT NULL,        -- signed: positive=credit, negative=debit/reservation
    balance_after BIGINT NOT NULL,        -- snapshot at time of entry
    reference     VARCHAR(200),           -- topup_id, sendRequestId, etc.
    created_by    VARCHAR(50),
    created_date  TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP,
    request_id    VARCHAR(100),
    session_id    TEXT
);
CREATE INDEX idx_ledger_client_date ON main.credit_ledger_entry(client_id, created_date DESC);

-- Top-up requests
CREATE TABLE main.topup_request (
    id              BIGINT PRIMARY KEY,
    client_id       BIGINT NOT NULL REFERENCES main.client_account(id),
    amount          BIGINT NOT NULL CHECK (amount > 0),
    transaction_id  VARCHAR(200) NOT NULL,
    payment_type    VARCHAR(50) NOT NULL,
    account_number  VARCHAR(50),
    topup_status    VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_at     TIMESTAMP,
    rejected_at     TIMESTAMP,
    created_by      VARCHAR(50),
    created_date    TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP,
    request_id      VARCHAR(100),
    session_id      TEXT,
    CONSTRAINT uq_topup_client_txn UNIQUE (client_id, transaction_id)
);
CREATE INDEX idx_topup_client_status ON main.topup_request(client_id, topup_status);
```

**Why `BIGINT` for balance/amount (not `DECIMAL`):** Credits are SMS segments — whole numbers only. Integer arithmetic is exact and avoids floating-point edge cases. No currency values are stored; the "unit" is always `SMS_SEGMENT`.

### Entity: ClientCreditBalance (Lock Row)

```java
// Source: codebase pattern — extends AbstractAuditingEntity like all other entities
@Entity
@Table(name = "client_credit_balance", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCreditBalance extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false, unique = true)
    private Long clientId;

    @Column(name = "balance", nullable = false)
    private long balance;   // NOT Long — balance is never null; use primitive

    // Getters/setters
}
```

### Repository: Pessimistic Lock Query

```java
// Source: Spring Data JPA @Lock annotation — generates SELECT ... FOR UPDATE on PostgreSQL
// vladmihalcea.com/spring-data-jpa-locking/ + logicbig.com/tutorials/spring-framework/spring-data/pessimistic-locking-and-lock-annotation.html
public interface ClientCreditBalanceRepository extends JpaRepository<ClientCreditBalance, Long> {

    Optional<ClientCreditBalance> findByClientId(Long clientId);  // non-locking read for balance query

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ClientCreditBalance b WHERE b.clientId = :clientId")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<ClientCreditBalance> findByClientIdForUpdate(@Param("clientId") Long clientId);
}
```

### Service: Credit Reservation (called by Phase 3 SMS send)

```java
// Source: pattern documented in Phase 2 research; uses codebase AbstractAuditingEntity conventions
@Service
@Transactional
public class CreditService {

    public long reserveCredits(Long clientId, long amount, String reference) {
        ClientCreditBalance lockRow = balanceRepo
            .findByClientIdForUpdate(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Balance row missing for client " + clientId));

        long currentBalance = lockRow.getBalance();
        if (currentBalance < amount) {
            throw new InsufficientBalanceException(clientId, currentBalance, amount);
        }
        long newBalance = currentBalance - amount;

        // Write ledger entry (balance_after computed here, not derived later)
        CreditLedgerEntry entry = CreditLedgerEntry.builder()
            .clientId(clientId)
            .entryType(LedgerEntryType.SMS_RESERVATION)
            .amount(-amount)              // negative — debit
            .balanceAfter(newBalance)
            .reference(reference)
            .status(EntityStatus.ACTIVE)
            .build();
        ledgerRepo.save(entry);

        lockRow.setBalance(newBalance);
        balanceRepo.save(lockRow);

        return newBalance;  // returned for send response: available_balance_after_reservation
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long clientId) {
        ClientCreditBalance row = balanceRepo.findByClientId(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client has no balance row"));
        return new BalanceResponse(
            row.getBalance(),
            "segments",
            "SMS_SEGMENT",
            row.getLastModifiedDate()
        );
    }
}
```

### Service: Top-Up Approval

```java
// Source: codebase patterns; @Transactional on service methods
@Transactional
public TopupStatusResponse approveTopup(Long topupId) {
    // Lock the topup row to prevent double-approval
    TopupRequestEntity topup = topupRepo.findByIdForUpdate(topupId)
        .orElseThrow(() -> new ResourceNotFoundException("topup_id not found"));

    if (topup.getTopupStatus() != TopupStatus.PENDING_APPROVAL) {
        throw new ConflictException("Top-up is already " + topup.getTopupStatus());
    }

    // Credit the balance — this internally acquires client_credit_balance FOR UPDATE
    creditService.applyTopupApproval(topup.getClientId(), topup.getAmount(),
                                     topup.getId().toString());

    topup.setTopupStatus(TopupStatus.APPROVED);
    topup.setApprovedAt(Instant.now());
    topupRepo.save(topup);

    return toResponse(topup);
}
```

### API Contract Response Shapes (v8 exact match)

```java
// GET /v1/credits/balance
public record BalanceResponse(
    @JsonProperty("available_balance") long availableBalance,
    String unit,
    String currency,
    @JsonProperty("last_updated_at") Instant lastUpdatedAt
) {}

// GET /v1/credits/ledger  (entries array; add page/size query params)
public record LedgerHistoryResponse(List<LedgerEntryDto> entries) {}

public record LedgerEntryDto(
    Instant timestamp,
    LedgerEntryType type,
    long amount,
    @JsonProperty("balance_after") long balanceAfter,
    String reference
) {}

// POST /v1/credits/topups response
public record CreateTopupResponse(
    @JsonProperty("topup_id") String topupId,  // "top_" + entity.getId()
    TopupStatus status,                         // PENDING_APPROVAL
    @JsonProperty("requested_at") Instant requestedAt
) {}

// GET /v1/credits/topups/{topup_id} response
public record TopupStatusResponse(
    @JsonProperty("topup_id") String topupId,
    long amount,
    TopupStatus status,
    @JsonProperty("approved_at") Instant approvedAt
) {}
```

**topup_id serialization:** The v8 contract shows `"topup_id": "top_12345"`. Map as `"top_" + entity.getId().toString()` at the service/resource layer. No separate string ID column needed. Parse incoming `{topup_id}` path variable by stripping `"top_"` prefix and parsing as `Long`.

---

## API Contract Alignment

All endpoint paths, HTTP methods, and response shapes derived directly from `requirements/client-facing_API_contract_v8.md`.

### Client-Facing Endpoints (API key auth, `/v1/api/**` chain — `@Order(1)`)

| Method | Path | Controller | Requirement |
|--------|------|-----------|-------------|
| GET | `/v1/credits/balance` | `CreditResource.getBalance()` | CREDIT-01 |
| GET | `/v1/credits/ledger` | `CreditResource.getLedgerHistory()` | CREDIT-02 |
| POST | `/v1/credits/topups` | `TopupResource.createTopup()` | TOPUP-01 |
| GET | `/v1/credits/topups/{topup_id}` | `TopupResource.getTopupStatus()` | TOPUP-02 |

### Admin Endpoints (JWT auth, `/api/admin/**` chain — `@Order(2)`)

| Method | Path | Controller | Requirement |
|--------|------|-----------|-------------|
| GET | `/api/admin/clients` | `AdminClientResource.getAllClients()` | ADMIN-03 |
| PUT | `/api/admin/topups/{topup_id}/approve` | `AdminTopupResource.approve()` | ADMIN-02 |
| PUT | `/api/admin/topups/{topup_id}/reject` | `AdminTopupResource.reject()` | ADMIN-02 |

**Note on client_id derivation:** Client-facing endpoints never accept `client_id` in the request. Extract it from `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` (which is a `Long` clientId set by `ApiKeyAuthenticationFilter` in Phase 1).

### HTTP Status Codes (v8 alignment)

| Scenario | Status | Error code |
|----------|--------|-----------|
| Balance retrieved | 200 OK | — |
| Top-up created | 200 OK | — (v8 contract uses 200, not 201) |
| Insufficient balance (Phase 3) | 400 Bad Request | `INSUFFICIENT_CLIENT_BALANCE` |
| Duplicate transaction_id | 409 Conflict | `DUPLICATE_TRANSACTION_ID` |
| Top-up not found | 404 Not Found | `TOPUP_NOT_FOUND` |
| Top-up already processed | 409 Conflict | `TOPUP_ALREADY_PROCESSED` |

---

## Index Strategy

Indices needed for p95 < 100ms at initial load (< 100k clients, < 1M ledger entries):

| Table | Index | Query it supports |
|-------|-------|-------------------|
| `client_credit_balance` | `UNIQUE (client_id)` — already implied by column constraint | `findByClientId`, `findByClientIdForUpdate` |
| `credit_ledger_entry` | `(client_id, created_date DESC)` composite | `findByClientIdOrderByCreatedDateDesc` (ledger history) |
| `topup_request` | `(client_id, topup_status)` | `findByClientIdAndTopupStatus` (admin view pending) |
| `topup_request` | `UNIQUE (client_id, transaction_id)` | TOPUP-03 uniqueness |

**Balance query performance:** The non-locking balance read is `SELECT balance FROM client_credit_balance WHERE client_id = ?` — a single row lookup on a unique index. This is O(1) and will be well under 1ms, trivially meeting p95 < 100ms (Source: PostgreSQL index behavior — single-row unique index lookup).

**Ledger history performance:** Pagination with `ORDER BY created_date DESC LIMIT 50` on the composite index `(client_id, created_date DESC)` avoids a sort. PostgreSQL can satisfy this query using the index directly. For a client with 100k ledger entries, this query returns page 1 in < 5ms. MEDIUM confidence on this estimate without benchmarking.

---

## Integration Points with Phase 1

| Phase 1 Artifact | Phase 2 Usage |
|------------------|---------------|
| `ClientEntity` (`client_account` table) | FK target for `client_credit_balance.client_id` and `topup_request.client_id` and `credit_ledger_entry.client_id` |
| `ClientService.createClient()` | Must be extended to also insert a `ClientCreditBalance` row with `balance=0` |
| `ApiKeyAuthenticationFilter` | Sets `clientId` as `Authentication.principal` — extracted in all credit/topup controllers |
| `AbstractAuditingEntity` | All new entities extend it — provides `created_date` (ledger entry timestamp), `request_id`, audit columns |
| `BaseEntity` (`@Tsid`) | All new entities inherit TSID primary key generation |
| `AdminClientResource` (`/api/admin/clients`) | Must add `GET` endpoint returning client + balance; currently only has `POST` |
| `ClientRepository` | Add `findAllWithBalance()` projection query for ADMIN-03 |

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Mutable `balance` column on account table | Append-only ledger + lock row aggregate | Auditability; no silent drift |
| `SELECT ... WHERE balance >= amount; UPDATE balance = balance - amount` (two round-trips, race condition) | `SELECT FOR UPDATE` + check + write in one transaction | Atomic — balance never goes negative under concurrency |
| Optimistic retry for balance deductions | Pessimistic `FOR UPDATE` for hard financial constraints | No retry complexity; deterministic outcome; correct for "must never overspend" |
| Spring State Machine for approval workflows | Enum field + service-layer guard | Avoids heavy dependency for a 3-state, 2-transition workflow |

**Deprecated/outdated:**
- `LockModeType.PESSIMISTIC_FORCE_INCREMENT`: Combines `FOR UPDATE` with version increment. Not needed here — use plain `PESSIMISTIC_WRITE`.
- `@Version` optimistic locking for balance: Incorrect approach for this use case (allows overspend under high contention if retries are not perfect).

---

## Open Questions

1. **Lock contention under bulk SMS sends**
   - What we know: `SELECT FOR UPDATE` on the balance lock row serializes all concurrent sends for one client. If a client sends bursts of 100 concurrent requests, each waits for the previous lock to release.
   - What's unclear: Whether the 2000ms lock timeout is sufficient or whether queue-like serialization is acceptable. For v1 with Cameroon-only clients at 10 req/s rate limit, contention is bounded.
   - Recommendation: Accept serialization for v1. Revisit if benchmarks show p95 > 50ms under load. The rate limiter (10 req/s per client from Phase 1) caps the worst-case lock queue depth.

2. **TOPUP_PENDING ledger entry amount**
   - What we know: v8 contract says "Ledger entry created: TOPUP_PENDING" when a top-up is submitted, but the response shows `status: PENDING_APPROVAL` and does not show a balance change.
   - What's unclear: Whether the `TOPUP_PENDING` entry should have `amount=0` (informational) or carry the pending amount as a separate column with no effect on balance.
   - Recommendation: Use `amount=0` for `TOPUP_PENDING`. The entry is an audit record documenting that a top-up was requested. The balance only changes on `TOPUP_APPROVED`. This keeps the SUM-based balance invariant clean.

3. **Idempotency for top-up creation (duplicate HTTP request)**
   - What we know: TOPUP-03 requires `transaction_id` unique per client. A duplicate POST with the same `transaction_id` catches `DataIntegrityViolationException`.
   - What's unclear: Should the 409 response return the existing topup's ID (idempotent return) or just an error?
   - Recommendation: Return 409 with `DUPLICATE_TRANSACTION_ID` error and no body (consistent with v8 error format). The client can query by `topup_id` if it needs the existing record.

---

## Sources

### Primary (HIGH confidence)
- Codebase (verified directly): `ClientEntity.java`, `ClientRepository.java`, `AbstractAuditingEntity.java`, `BaseEntity.java`, `EntityStatus.java`, `ClientService.java`, `AdminClientResource.java`, `V2__client_api_key.sql`, `ARCHITECTURE.md`, `requirements/client-facing_API_contract_v8.md`
- [PostgreSQL Official Docs — Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html) — `SELECT FOR UPDATE` semantics, row-level lock conflicts, deadlock patterns
- [Spring Data JPA @Lock with PESSIMISTIC_WRITE](https://www.logicbig.com/tutorials/spring-framework/spring-data/pessimistic-locking-and-lock-annotation.html) — `@Lock` on repository method generates `FOR UPDATE` for PostgreSQL
- [Vlad Mihalcea — Spring Data JPA Locking](https://vladmihalcea.com/spring-data-jpa-locking/) — `PESSIMISTIC_WRITE` → `FOR UPDATE` SQL translation confirmed

### Secondary (MEDIUM confidence)
- [Paul Gross — Ledger Implementation in PostgreSQL (2025)](https://www.pgrs.net/2025/03/24/pgledger-ledger-implementation-in-postgresql/) — materialized balance row vs SUM tradeoffs
- [Lukman Hakim — How Indexing Supercharges SUM Queries in PostgreSQL](https://medium.com/@lukmanfreedom/how-indexing-can-supercharge-your-count-and-sum-queries-in-postgresql-8ba3f8523f61) — composite index on (client_id, ...) can reduce SUM queries from 187ms to <50ms
- [Baeldung — Pessimistic Locking in JPA](https://www.baeldung.com/jpa-pessimistic-locking) — `PESSIMISTIC_WRITE` semantics, lock timeout via `QueryHint`
- [Baeldung — Enabling Transaction Locks in Spring Data JPA](https://www.baeldung.com/java-jpa-transaction-locks) — `@QueryHints` with `jakarta.persistence.lock.timeout`

### Tertiary (LOW confidence)
- WebSearch results on ledger design patterns (2025) — confirmed append-only ledger with lock-row aggregate is established pattern
- WebSearch on offset vs keyset pagination for ledger history — confirmed offset is acceptable for bounded history sizes at v1 scale

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in `pom.xml`; no new dependencies needed
- Ledger schema design: HIGH — verified against v8 contract + PostgreSQL official docs + codebase base classes
- Balance lock row + `SELECT FOR UPDATE`: HIGH — confirmed via PostgreSQL docs + Spring Data JPA `@Lock` docs
- `balance_after` stored at write time: HIGH — logical consequence of lock-row pattern; avoids running-total re-computation
- Pessimistic vs optimistic recommendation: HIGH — pessimistic is the correct choice for hard "never negative" invariant
- p95 < 100ms achievability: MEDIUM — based on O(1) unique-index lookup for balance read; ledger history with composite index; no direct benchmark
- Pagination approach (offset): MEDIUM — offset is correct for v1 scale; keyset would be better at > 1M entries per client

**Research date:** 2026-03-10
**Valid until:** 2026-06-10 (Spring Boot 3.x, PostgreSQL locking behavior, and Spring Data JPA `@Lock` are stable; no anticipated breaking changes)
