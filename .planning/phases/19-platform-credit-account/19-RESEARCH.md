# Phase 19: Platform Credit Account - Research

**Researched:** 2026-03-17
**Domain:** Platform-level credit accounting in a Spring Boot 3.5.11 / JPA / PostgreSQL codebase
**Confidence:** HIGH — all findings drawn directly from source code inspection

---

## Summary

Phase 19 adds a platform-level twin of the existing per-client credit system. The project already has a fully working pattern for this exact problem: `client_credit_balance` (single lock row per entity) + `credit_ledger_entry` (append-only ledger) + pessimistic SELECT FOR UPDATE locking inside `CreditService.applyLedgerEntry()`. Platform credit is a structural duplicate of that pattern but scoped to a singleton (the platform itself, not a per-client row).

The only non-trivial part is modifying `TopupService.approve()` to debit the platform balance atomically in the same transaction that credits the client. Both locks must be held before either write occurs to prevent double-spend. The existing code already acquires two pessimistic locks in a single transaction (topup row + client credit balance row), so adding a third (platform balance row) follows the established pattern without new risk.

The new endpoints belong in the existing `billing` module under `/api/admin/platform/credits/**`. No new module is required. The security registration pattern (`AppEndpoints` constant + `SECURED_MAPPINGS` entry) must be applied.

**Primary recommendation:** Mirror the `ClientCreditBalance` / `CreditLedgerEntry` / `CreditService.applyLedgerEntry()` pattern for the platform account. Add a `PlatformCreditService` that owns the platform lock row and ledger writes. Modify `TopupService.approve()` to call `PlatformCreditService.debitForTopup()` after (and within the same transaction as) the client credit. Reject at the service layer by throwing a new `InsufficientPlatformBalanceException` before any writes.

---

## Standard Stack

No new libraries are needed. All patterns are satisfied by the existing stack.

### Core (already in project)
| Component | Purpose | Pattern location |
|-----------|---------|-----------------|
| Spring Data JPA | Entity persistence + repositories | `billing/repo/` |
| `@Lock(PESSIMISTIC_WRITE)` | SELECT FOR UPDATE via JPA | `ClientCreditBalanceRepository.findByClientIdForUpdate()` |
| `@QueryHints` lock timeout 2000ms | 2-second lock timeout | Same repository |
| `@Transactional` (service layer) | Transaction boundary | `CreditService`, `TopupService` |
| `AbstractAuditingEntity` | `created_by`, `created_date`, `request_id`, `session_id`, `status` columns | `billing/repo/CreditLedgerEntry.java` |
| `BaseEntity` (`@Tsid`) | Auto-generated TSID Long PK | `common/persistence/BaseEntity.java` |
| `ApplicationEventPublisher` | Audit event publishing | `TopupService.approve()` |
| `AuditEventType` | Audit event enum | `gateway/audit/contract/AuditEventType.java` |
| `ApplicationException` + `ErrorCode` | Typed exceptions | `common/exception/` |
| `ApiAdvice` | Global exception-to-HTTP mapping | `security/api/ApiAdvice.java` |
| `AppEndpoints` | Security path constants | `security/config/AppEndpoints.java` |

**Installation:** none required.

---

## Architecture Patterns

### Recommended Package Layout

New files go inside the existing `billing` module:

```
gateway/billing/
├── api/
│   └── AdminPlatformCreditResource.java   (NEW)
├── contract/
│   ├── PlatformLedgerEntryType.java        (NEW enum)
│   ├── PlatformBalanceResponse.java        (NEW record)
│   ├── PlatformLedgerEntryDto.java         (NEW record)
│   ├── PlatformLedgerHistoryResponse.java  (NEW record)
│   ├── RecordNexahPurchaseRequest.java     (NEW record)
│   ├── RecordNexahPurchaseResponse.java    (NEW record)
│   └── InsufficientPlatformBalanceException.java  (NEW exception)
├── repo/
│   ├── PlatformCreditBalance.java          (NEW entity)
│   ├── PlatformCreditBalanceRepository.java (NEW repository)
│   ├── PlatformCreditLedgerEntry.java      (NEW entity)
│   └── PlatformCreditLedgerRepository.java (NEW repository)
└── service/
    └── PlatformCreditService.java          (NEW service)
```

`TopupService.java` — **modified** (inject `PlatformCreditService`, call `debitForTopup()` inside `approve()`).

`AppEndpoints.java` — **modified** (add `ADMIN_PLATFORM_CREDITS` constant and `SECURED_MAPPINGS` entry).

`ApiAdvice.java` — **modified** (add `@ExceptionHandler` for `InsufficientPlatformBalanceException`).

`AuditEventType.java` — **modified** (add `NEXAH_PURCHASE_RECORDED`).

### Pattern 1: Singleton Balance Row + SELECT FOR UPDATE

The platform has exactly one balance row (no `client_id` FK; the singleton is identified by a known constant ID or simply by `findFirst()`). The same pessimistic lock pattern as `ClientCreditBalanceRepository` is used:

```java
// Source: billing/repo/ClientCreditBalanceRepository.java (existing pattern to replicate)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM PlatformCreditBalance b")
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
Optional<PlatformCreditBalance> findForUpdate();
```

The singleton row is bootstrapped at startup via a `@EventListener(ApplicationReadyEvent)` or a Flyway migration INSERT if the row is absent (recommended: INSERT in migration so no startup code is needed).

### Pattern 2: applyPlatformLedgerEntry in PlatformCreditService

Mirror of `CreditService.applyLedgerEntry()` — acquire lock, compute new balance, reject if negative, write ledger entry, save balance row.

```java
// Source: billing/service/CreditService.java lines 106-135 (existing pattern to replicate)
@Transactional
public void applyLedgerEntry(PlatformLedgerEntryType type, long amount, String reference) {
    PlatformCreditBalance lockRow = balanceRepository.findForUpdate()
            .orElseThrow(() -> new ResourceNotFoundException("Platform balance not initialized", "platform_credit_balance"));

    long newBalance = lockRow.getBalance() + amount;

    if (newBalance < 0) {
        throw new InsufficientPlatformBalanceException(
                "Platform balance insufficient",
                lockRow.getBalance(),
                Math.abs(amount));
    }

    PlatformCreditLedgerEntry entry = PlatformCreditLedgerEntry.builder()
            .entryType(type)
            .amount(amount)
            .balanceAfter(newBalance)
            .reference(reference)
            .status(EntityStatus.ACTIVE)
            .build();
    ledgerRepository.save(entry);

    lockRow.setBalance(newBalance);
    balanceRepository.save(lockRow);
}
```

### Pattern 3: Atomic TopupApproval Modification

`TopupService.approve()` runs in a single `@Transactional` method. It currently holds two locks: topup row and client credit balance row. Add platform balance lock as the third lock, acquired (via `PlatformCreditService.applyLedgerEntry()`) after client credit is applied. All three writes succeed or all roll back together.

Lock acquisition order to avoid deadlocks (always acquire in the same order across all callers):
1. Topup request row (`findByIdForUpdate`)
2. Client credit balance row (inside `CreditService.applyLedgerEntry`)
3. Platform credit balance row (inside `PlatformCreditService.applyLedgerEntry`)

```java
// In TopupService.approve() — modified section (source: billing/service/TopupService.java lines 131-155)
// After: creditService.applyLedgerEntry(entity.getClientId(), LedgerEntryType.TOPUP_APPROVED, entity.getAmount(), topupId);
// Add:
platformCreditService.applyLedgerEntry(
    PlatformLedgerEntryType.TOPUP_DEBIT,
    -entity.getAmount(),      // signed: negative = debit
    topupId
);
```

The `InsufficientPlatformBalanceException` thrown inside `platformCreditService.applyLedgerEntry()` will propagate out of `approve()`, roll back the transaction (client credit write is undone), and return HTTP 422 (or 400 — see error section).

### Pattern 4: Admin Endpoint Convention

```java
// Source: billing/api/AdminTopupResource.java, account/api/AdminClientResource.java
@RestController
@RequestMapping("/api/admin/platform/credits")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformCreditResource {

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public RecordNexahPurchaseResponse recordPurchase(@Valid @RequestBody RecordNexahPurchaseRequest request) { ... }

    @GetMapping("/balance")
    public PlatformBalanceResponse getBalance() { ... }

    @GetMapping("/ledger")
    public PlatformLedgerHistoryResponse getLedgerHistory(
            @RequestParam(required = false) PlatformLedgerEntryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { ... }
}
```

### Anti-Patterns to Avoid

- **Deriving balance from ledger SUM at query time:** The existing system stores `balance_after` on each entry and keeps a lock row with current balance. Do not replace this with a `SELECT SUM(amount)` query — that is not how the existing system works and would bypass the pessimistic lock pattern.
- **Optimistic locking for the balance row:** The per-client balance uses pessimistic locking (`SELECT FOR UPDATE`). Platform balance must use the same pattern. A `@Version` field would create a race-condition window on the platform singleton.
- **Putting platform balance in `common/`:** It belongs in `billing/` — it is a billing domain concept, not a cross-cutting concern.
- **Separate Spring Security filter chain entry for platform endpoints:** Use the same pattern as `ADMIN_CREDITS` — a constant in `AppEndpoints` and an entry in `SECURED_MAPPINGS`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Singleton balance row ID | Custom `@Sequence` or application-managed ID | Flyway INSERT with a fixed TSID value or `findFirst()` after guaranteed-single row | Simpler; the locking contract only needs one row |
| Lock acquisition | Java `synchronized` or Redis lock | JPA `@Lock(PESSIMISTIC_WRITE)` + `@QueryHint` timeout | Already established pattern in the codebase |
| Pagination | Manual list slicing | `PageRequest.of(page, size)` + `Page<T>` | Already used in `CreditService.getLedgerHistory()` |
| Error HTTP mapping | Custom filter | `@ExceptionHandler` in `ApiAdvice` | Already the project pattern |
| Audit trail | Custom logging | `ApplicationEventPublisher` + `DomainAuditEvent` | Already used in `TopupService.approve()` |

---

## Common Pitfalls

### Pitfall 1: Transaction Rollback on Platform Balance Rejection

**What goes wrong:** If `InsufficientPlatformBalanceException` is thrown *after* `creditService.applyLedgerEntry()` writes the client credit, but the exception is caught somewhere and swallowed or re-thrown outside the transaction boundary, the client credit write persists while the platform write does not.

**Why it happens:** Forgetting that `@Transactional` only rolls back for unchecked exceptions by default, or adding a try-catch that re-throws a checked exception.

**How to avoid:** `InsufficientPlatformBalanceException` must extend `RuntimeException` (via `ApplicationException`) — it does in this codebase. Do not wrap it in a checked exception or catch-and-return inside `approve()`.

**Warning signs:** Client credit balance increases but platform balance does not decrease.

### Pitfall 2: Lock Order Deadlock

**What goes wrong:** If another code path acquires platform balance lock then client credit balance lock (opposite order), and `approve()` acquires client balance then platform balance, a deadlock can occur under concurrent requests.

**Why it happens:** Inconsistent lock acquisition order across code paths.

**How to avoid:** Enforce a single canonical lock order across all code paths that acquire multiple locks: topup row → client credit balance → platform credit balance. Document this order explicitly in comments.

**Warning signs:** Deadlock errors in logs under concurrent approval load.

### Pitfall 3: Singleton Row Bootstrap

**What goes wrong:** `findForUpdate()` throws `ResourceNotFoundException` on a fresh environment because the singleton balance row was never created.

**Why it happens:** Relying on application startup code to create the row, which may not run or may race.

**How to avoid:** Insert the singleton row in the Flyway migration itself (e.g., `V11__platform_credit_account.sql` inserts one row with a fixed ID). This is deterministic and idempotent.

**Warning signs:** `ResourceNotFoundException: Platform balance not initialized` on first call after deployment.

### Pitfall 4: AppEndpoints Not Registered

**What goes wrong:** New `/api/admin/platform/credits/**` endpoints are accessible without authentication because the path was not added to `AppEndpoints.SECURED_MAPPINGS`.

**Why it happens:** The existing entries in `SECURED_MAPPINGS` are the authoritative list of admin-protected paths. Forgetting to add the new constant means the JWT filter chain does not enforce `ROLE_ADMIN`.

**How to avoid:** Add `ADMIN_PLATFORM_CREDITS = "/api/admin/platform/credits/**"` to `AppEndpoints` and add the mapping entry in the static block. The `@PreAuthorize("hasRole('ADMIN')")` on the controller is belt-and-suspenders but does not replace the filter chain registration.

### Pitfall 5: SHORTFALL_ABSORPTION Entry Type

**What goes wrong:** PLAT-03 requires a `SHORTFALL_ABSORPTION` entry type for client overdrafts absorbed by the platform. Phase 19 is not required to implement the overdraft logic itself (that is a later phase), but the enum value must exist in `PlatformLedgerEntryType` and the column constraint (`VARCHAR(30)`) must accommodate the value.

**Why it happens:** Only implementing the two types needed immediately (`NEXAH_PURCHASE`, `TOPUP_DEBIT`) and forgetting the third.

**How to avoid:** Define all three values in `PlatformLedgerEntryType` in Phase 19 even if `SHORTFALL_ABSORPTION` is not yet written by any code path.

---

## Code Examples

### Entity: PlatformCreditBalance

```java
// Source: billing/repo/ClientCreditBalance.java (pattern to replicate)
@Entity
@Table(name = "platform_credit_balance", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlatformCreditBalance extends AbstractAuditingEntity {

    // No clientId — singleton. One row, always.

    @Column(name = "balance", nullable = false)
    private long balance;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
```

### Entity: PlatformCreditLedgerEntry

```java
// Source: billing/repo/CreditLedgerEntry.java (pattern to replicate)
@Entity
@Table(name = "platform_credit_ledger_entry", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlatformCreditLedgerEntry extends AbstractAuditingEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private PlatformLedgerEntryType entryType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "reference", length = 200)
    private String reference;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
```

### Repository: PlatformCreditBalanceRepository

```java
// Source: billing/repo/ClientCreditBalanceRepository.java (pattern to replicate)
public interface PlatformCreditBalanceRepository extends JpaRepository<PlatformCreditBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PlatformCreditBalance b")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<PlatformCreditBalance> findForUpdate();

    @Query("SELECT b FROM PlatformCreditBalance b")
    Optional<PlatformCreditBalance> findBalance();
}
```

### Flyway Migration V11 (table creation + singleton row)

```sql
-- V11__platform_credit_account.sql

-- Singleton lock row: one row for the platform
CREATE TABLE main.platform_credit_balance (
    id                   BIGINT PRIMARY KEY,
    balance              BIGINT NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);

-- Append-only platform ledger
CREATE TABLE main.platform_credit_ledger_entry (
    id            BIGINT PRIMARY KEY,
    entry_type    VARCHAR(30) NOT NULL,
    amount        BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reference     VARCHAR(200),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by           VARCHAR(50),
    created_date         TIMESTAMP,
    last_modified_by     VARCHAR(50),
    last_modified_date   TIMESTAMP,
    request_id           VARCHAR(100),
    session_id           TEXT
);

CREATE INDEX idx_platform_ledger_type_date
    ON main.platform_credit_ledger_entry(entry_type, created_date DESC);

-- Bootstrap the singleton balance row with balance = 0
-- ID value: a fixed TSID-compatible Long; using a known safe value
INSERT INTO main.platform_credit_balance (id, balance, status, created_date)
VALUES (1, 0, 'ACTIVE', NOW());
```

**Note on ID value for singleton INSERT:** The project uses `@Tsid` for IDs (from `io.hypersistence.utils`). For the singleton row, a fixed ID of `1` will work for the INSERT because `@Tsid` only generates IDs for new entity instances created via JPA — it does not block a raw SQL INSERT. The repository query uses `findForUpdate()` which does `SELECT ... FROM platform_credit_balance` without a WHERE clause, so the ID value is irrelevant to application code.

### Error Code: InsufficientPlatformBalanceException

```java
// Source: billing/contract/InsufficientBalanceException.java (pattern to replicate)
// New error enum: add INSUFFICIENT_PLATFORM_BALANCE to a BillingError (or PlatformError) enum
public enum PlatformError implements ErrorCode {
    INSUFFICIENT_PLATFORM_BALANCE;

    @Override
    public String getErrorCode() { return this.name(); }
}

public class InsufficientPlatformBalanceException extends ApplicationException {
    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientPlatformBalanceException(String message, long currentBalance, long requestedAmount) {
        super(message, PlatformError.INSUFFICIENT_PLATFORM_BALANCE);
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
    // getters
}
```

**HTTP status for this exception:** Use 422 Unprocessable Entity to distinguish from `InsufficientBalanceException` (client balance, HTTP 400). This signals "the request was well-formed but cannot be processed because platform funds are insufficient." Add to `ApiAdvice`:

```java
@ExceptionHandler(InsufficientPlatformBalanceException.class)
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public ErrorDto insufficientPlatformBalanceHandler(final InsufficientPlatformBalanceException exception) {
    return logErrorAndReturnDTO(exception, "Platform balance insufficient to approve this top-up.", "INSUFFICIENT_PLATFORM_BALANCE");
}
```

### PlatformLedgerEntryType enum

```java
// Location: billing/contract/PlatformLedgerEntryType.java
public enum PlatformLedgerEntryType {
    NEXAH_PURCHASE,      // Admin records credits bought from Nexah — positive amount
    TOPUP_DEBIT,         // Client top-up approved — negative amount
    SHORTFALL_ABSORPTION // Client overdraft absorbed by platform — negative amount (future phases)
}
```

### Paginated ledger query (filterable by type)

```java
// Source: billing/repo/CreditLedgerRepository.java (Page<T> pattern)
// In PlatformCreditLedgerRepository:
Page<PlatformCreditLedgerEntry> findByEntryTypeOrderByCreatedDateDesc(
        PlatformLedgerEntryType entryType, Pageable pageable);

Page<PlatformCreditLedgerEntry> findAllByOrderByCreatedDateDesc(Pageable pageable);
```

Or use a single JPQL query with optional filter:

```java
@Query("SELECT e FROM PlatformCreditLedgerEntry e " +
       "WHERE (:type IS NULL OR e.entryType = :type) " +
       "ORDER BY e.createdDate DESC")
Page<PlatformCreditLedgerEntry> findByOptionalType(
        @Param("type") PlatformLedgerEntryType type, Pageable pageable);
```

### Reference field values by entry type

| Entry Type | `reference` field value |
|------------|------------------------|
| `NEXAH_PURCHASE` | Admin-supplied receipt/invoice number (from request body), or auto-generated `nexah_<id>` if not provided |
| `TOPUP_DEBIT` | The `topupId` string (e.g., `top_12345`) — same format as used in client ledger |
| `SHORTFALL_ABSORPTION` | The `topupId` or send request ID that caused the shortfall |

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| Optimistic lock (`@Version`) for credit balance | Pessimistic lock (`SELECT FOR UPDATE`) | Established in this codebase since V3 migration |
| Balance derived from ledger SUM | Materialized balance row + append-only ledger | Both tables in this codebase |

---

## Open Questions

1. **HTTP status for INSUFFICIENT_PLATFORM_BALANCE**
   - What we know: existing `InsufficientBalanceException` (client) maps to HTTP 400.
   - What's unclear: should platform balance rejection also be 400, or 422, or 409? The requirements say "approval is rejected" which implies the request is refused, not invalid.
   - Recommendation: use 422 Unprocessable Entity — it signals the request was valid but cannot proceed due to business state. This differentiates it from the client 400. If project convention requires only 400/409, use 409 Conflict with error code `INSUFFICIENT_PLATFORM_BALANCE`.

2. **Singleton row ID in Flyway INSERT**
   - What we know: `@Tsid` generates IDs automatically; a fixed `id=1` in the INSERT may technically conflict with the TSID range used by Hibernate.
   - What's unclear: whether TSID values can collide with `1` in practice (TSID encodes timestamp bits, so very low integer values are safe for a while).
   - Recommendation: use a fixed large known value like `100000000000000001L` or simply let application startup insert the row if absent (using `findBalance()` then INSERT if empty). The migration INSERT approach is simpler; verify `io.hypersistence.utils` TSID range to confirm `1` is safe long-term. Alternative: insert using `nextval` of a sequence.

3. **`RecordNexahPurchaseRequest` validation**
   - What we know: amount must be positive (same constraint as topup_request).
   - What's unclear: whether a reference/receipt field should be required or optional.
   - Recommendation: make `reference` optional (nullable), consistent with `CreditLedgerEntry.reference`. Admin can record without a reference.

---

## Sources

### Primary (HIGH confidence)
- Direct source code inspection of `billing/repo/`, `billing/service/`, `billing/api/`, `billing/contract/` — all patterns derived from running production code in this repository
- `security/config/AppEndpoints.java` — security registration pattern
- `security/api/ApiAdvice.java` — exception handler registration pattern
- `common/persistence/BaseEntity.java`, `AbstractAuditingEntity.java` — entity base class patterns
- `db/migration/V3__credit_ledger.sql`, `V4__topup_request.sql` — DDL conventions

### Secondary (MEDIUM confidence)
- None required; all patterns are directly observable in the codebase

### Tertiary (LOW confidence)
- TSID value safety for `id=1` in Flyway INSERT — not verified against `io.hypersistence.utils` TSID spec; flagged as Open Question 2

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all patterns verified in source
- Architecture: HIGH — domain placement, package layout, and layering rules verified in ARCHITECTURE.md and existing modules
- Pitfalls: HIGH — derived from observed code patterns and known concurrent-write failure modes
- Flyway migration DDL: HIGH — matches V3 DDL column-for-column; TSID singleton insertion flagged LOW

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable codebase; no external dependency risk)
