# Phase 22: Final Booking & Segment Deviation - Research

**Researched:** 2026-03-17
**Domain:** Billing finalization, segment deviation detection, shortfall absorption, platform freeze
**Confidence:** HIGH — all findings come directly from codebase inspection of fully verified phases 19-21

## Summary

Phase 22 completes the billing lifecycle that phases 19-21 set up. When Nexah returns `total_sms_unit`
per recipient, the system must compare actual vs expected segments, settle the credit reservation
with the correct amount, optionally absorb shortfalls from the platform balance, freeze accounts if
needed, and create a `SEGMENT` or `PLATFORM_FREEZE` deviation alert for any non-zero deviation.

The codebase already has all the infrastructure needed: `CreditReservationService.debit()` and
`release()` for settling reservations, `PlatformCreditService.applyLedgerEntry()` for platform
debits, `ClientFreezeService.freeze()` and `PlatformFreezeService.freeze()` for freezing, and
`SmsFinalisedBillingListener` as the entry point that listens to `SmsFinalisedEvent`. Phase 22
replaces and substantially expands `SmsFinalisedBillingListener` with the full 6-scenario booking
logic, adds a segment deviation alert entity/service, and adds two new `LedgerEntryType` and
`AuditEventType` values.

**Primary recommendation:** Replace the current thin `SmsFinalisedBillingListener` with a new
`FinalBookingService` that owns the 6-scenario decision tree. Keep `SmsFinalisedBillingListener`
as a thin event dispatch shim that calls the new service. Add a `SegmentDeviationAlert` entity,
migration, and `SegmentDeviationService` that the booking service calls to persist SEGDEV alerts.

## Standard Stack

All existing project libraries and patterns are used. No new dependencies are needed.

### Core
| Library / Component | Version / Location | Purpose in Phase 22 |
|---------------------|-------------------|---------------------|
| Spring Events (`ApplicationEventPublisher`) | Spring Boot 3.5.11 | `SmsFinalisedEvent` already published by `SmsProviderReportListener`; Phase 22 listens |
| `CreditReservationService` | `billing.service` | `debit()` and `release()` settle the reservation |
| `CreditService.applyLedgerEntry()` | `billing.service` | Direct debit of extra segments from client balance (BOOK-04) |
| `PlatformCreditService.applyLedgerEntry()` | `billing.service` | `SHORTFALL_ABSORPTION` platform debit (BOOK-05/06) |
| `ClientFreezeService.freeze()` | `account.service` | Freeze client on shortfall (BOOK-05) |
| `PlatformFreezeService.freeze()` | `billing.service` | Freeze platform on total shortfall (BOOK-06) |
| Flyway | V14__... | New deviation alert table migration |
| JPA / Hibernate | via Spring Data | `SegmentDeviationAlert` entity + `PerRecipientDeviation` JSONB column |
| `@Transactional` | Spring | All booking logic must be in one transaction |
| Mockito + JUnit 5 | test scope | Unit tests for new `FinalBookingService` |

### Existing Enums That Need New Values

| Enum | File | New Value Needed | Used For |
|------|------|-----------------|---------|
| `LedgerEntryType` | `billing.contract` | `SMS_EXTRA_DEBIT` | BOOK-04: extra client debit beyond reservation |
| `PlatformLedgerEntryType` | `billing.contract` | Already has `SHORTFALL_ABSORPTION` | BOOK-05/06: platform absorbs shortfall |
| `AuditEventType` | `audit.contract` | `SEGMENT_DEVIATION`, `PLATFORM_FREEZE_SHORTFALL` | SEGDEV-02, BOOK-06 |

Note: `PlatformLedgerEntryType.SHORTFALL_ABSORPTION` already exists. No new platform ledger type needed.

**Installation:** No new dependencies required.

## Architecture Patterns

### Decision Tree for the 6 Booking Scenarios

```
Given: sendRequest (rawExpectedCredits, reservedCredits, reservationId, clientId)
       recipients (expectedSegments, segmentsConsumed per recipient)

Compute:
  actualTotal     = sum(recipient.segmentsConsumed)
  expectedTotal   = sendRequest.rawExpectedCredits        (= expectedSegments * N, unbuffered)
  reservedTotal   = sendRequest.reservedCredits           (= (expectedSegments+1) * N, buffered)
  delta           = actualTotal - expectedTotal           (negative = under, positive = over)

BOOK-01 (actualTotal < expectedTotal <= reservedTotal):
  → debit(clientId, reservationId, actualTotal)           // settles; returns over-reservation to client
  → createSegmentAlert(delta)

BOOK-02 (actualTotal == expectedTotal, actualTotal <= reservedTotal):
  → debit(clientId, reservationId, actualTotal)           // settles; buffer refunded automatically
  → NO alert

BOOK-03 (expectedTotal < actualTotal <= reservedTotal):
  → debit(clientId, reservationId, actualTotal)           // settles within reservation; buffer absorbs overage
  → createSegmentAlert(delta)

BOOK-04 (actualTotal > reservedTotal, client balance >= extra):
  extra = actualTotal - reservedTotal
  → debit(clientId, reservationId, reservedTotal)         // consume full reservation
  → creditService.applyLedgerEntry(clientId, SMS_EXTRA_DEBIT, -extra, ref)
  → createSegmentAlert(delta)

BOOK-05 (actualTotal > reservedTotal, client balance < extra):
  extra = actualTotal - reservedTotal
  clientAvail = clientBalance (from SELECT FOR UPDATE)
  shortfall = extra - clientAvail
  → debit(clientId, reservationId, reservedTotal)
  → creditService.applyLedgerEntry(clientId, SMS_EXTRA_DEBIT, -clientAvail, ref)   // drain client to zero
  → platformCreditService.applyLedgerEntry(SHORTFALL_ABSORPTION, -shortfall, ref)  // platform absorbs
  → clientFreezeService.freeze(clientId, "Shortfall: " + shortfall)
  → createSegmentAlert(delta, shortfall=shortfall, frozen=true)

BOOK-06 (BOOK-05 but platform balance < shortfall):
  platformAvail = platformBalance (from SELECT FOR UPDATE)
  unrecovered = shortfall - platformAvail
  → debit(clientId, reservationId, reservedTotal)
  → creditService.applyLedgerEntry(clientId, SMS_EXTRA_DEBIT, -clientAvail, ref)
  → platformCreditService absorb platformAvail (drain to zero)  // partial absorption
  → clientFreezeService.freeze(clientId, "Shortfall: " + shortfall)
  → platformFreezeService.freeze("Platform shortfall: " + unrecovered, unrecovered)
  → createSegmentAlert(delta, shortfall=shortfall, frozen=true, platformFrozen=true, unrecovered=unrecovered)
  → createPlatformFreezeAlert(...)
```

### Lock Ordering — CRITICAL

The established lock order (from STATE.md decision 19-02) must be preserved:

1. Reservation ledger entry (implicit: `debit()` loads and locks the balance row)
2. Client credit balance — `SELECT FOR UPDATE` in `applyLedgerEntry`
3. Platform credit balance — `SELECT FOR UPDATE` in `PlatformCreditService.applyLedgerEntry`

For BOOK-04: no new lock ordering concern — `CreditService.applyLedgerEntry()` acquires client balance lock, same as existing usage.

For BOOK-05/06: debit client first (lock 2), then platform (lock 3). This matches the existing lock order documented in `PlatformCreditService.applyLedgerEntry()` comment.

### New Service: FinalBookingService

```
gateway.billing.service.FinalBookingService
```

- `@Service @Transactional @Slf4j @RequiredArgsConstructor`
- Injected by: `SmsFinalisedBillingListener`
- Injects: `CreditReservationService`, `CreditService`, `PlatformCreditService`,
  `ClientFreezeService`, `PlatformFreezeService`, `SegmentDeviationService`,
  `ClientCreditBalanceRepository` (for lock + read balance before BOOK-04/05 decisions),
  `SendRequestRepository` (to load `rawExpectedCredits` and `reservedCredits`)

### Thin Event Shim: SmsFinalisedBillingListener (Modified)

The existing listener must be expanded to pass the full `SendRequest` context to `FinalBookingService`.
Currently `SmsFinalisedEvent` carries `actualSegments` (total) but not the per-recipient breakdown
needed for SEGDEV-03/04.

Two options:
- **Option A:** Extend `SmsFinalisedEvent` to include per-recipient segment data
- **Option B:** `FinalBookingService` loads per-recipient data from `SendRequestRecipientRepository`
  using the `sendRequestId`

**Use Option B.** `FinalBookingService` loads `SendRequest` (for `rawExpectedCredits`,
`reservedCredits`, `reservationId`, `clientId`) and all its `SendRequestRecipient` rows
(for `expectedSegments`, `segmentsConsumed`) inside the transaction. This avoids changing the
`SmsFinalisedEvent` record, which is an established API already tested by `SmsProviderReportListenerTest`.
`SmsFinalisedBillingListener.onSmsFinalized()` simply calls `finalBookingService.book(event.clientId(), event.sendRequestId(), event.actualSegments(), event.reservationId())`.

### New Entity: SegmentDeviationAlert

```
gateway.billing.repo.SegmentDeviationAlert
  - send_request_id_fk       BIGINT NOT NULL (FK to send_request.id)
  - send_request_ref         VARCHAR(200) NOT NULL (human-readable sendRequestId)
  - client_id                BIGINT NOT NULL
  - alert_type               VARCHAR(30) NOT NULL   -- 'SEGMENT' or 'PLATFORM_FREEZE'
  - expected_total           BIGINT NOT NULL
  - actual_total             BIGINT NOT NULL
  - delta                    BIGINT NOT NULL
  - shortfall_amount         BIGINT                 -- null when no shortfall
  - client_frozen            BOOLEAN NOT NULL DEFAULT FALSE
  - platform_frozen          BOOLEAN NOT NULL DEFAULT FALSE
  - unrecovered_amount       BIGINT                 -- null unless platform also ran dry
  - per_recipient_breakdown  JSONB NOT NULL         -- array of RecipientDeviation
  - financial_action         VARCHAR(30) NOT NULL   -- REFUNDED / EXTRA_DEBITED / SHORTFALL_ABSORBED
  - status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
  + standard audit columns
```

For the JSONB column, use `hypersistence-utils` `@Type(JsonType.class)` — already a project
dependency (`io.hypersistence:hypersistence-utils-hibernate-63 3.9.10`). This is the project's
existing pattern for JSON storage.

### New Service: SegmentDeviationService

```
gateway.billing.service.SegmentDeviationService
```

Single method: `createAlert(SegmentDeviationAlertData data)`. Receives a data record, builds
`SegmentDeviationAlert`, saves it. Optionally publishes an `AuditEventType.SEGMENT_DEVIATION`
domain event for the audit trail (consistent with how Phase 20 freeze services publish events).

### Recommended Project Structure Additions

```
gateway/billing/
├── repo/
│   ├── SegmentDeviationAlert.java      (new entity)
│   └── SegmentDeviationAlertRepository.java  (new repo)
├── service/
│   ├── FinalBookingService.java        (new — core 6-scenario logic)
│   ├── SegmentDeviationService.java    (new — alert persistence)
│   └── SmsFinalisedBillingListener.java  (modified — delegates to FinalBookingService)
└── contract/
    ├── LedgerEntryType.java            (modified — add SMS_EXTRA_DEBIT)
    └── DeviationAlertType.java         (new enum: SEGMENT, PLATFORM_FREEZE)

src/main/resources/db/migration/
└── V14__segment_deviation_alert.sql    (new)
```

### Anti-Patterns to Avoid

- **Calling `debit()` then `applyLedgerEntry()` in separate transactions:** Both must be in the same `@Transactional` context. If `debit()` succeeds but `applyLedgerEntry()` for the extra amount fails, credits are deducted from the reservation but the extra is never charged. Use `@Transactional` at class level on `FinalBookingService` (same pattern as all other billing services).

- **Reading client balance outside a lock:** In BOOK-04/05 decision logic, the code must read the live client balance using `ClientCreditBalanceRepository.findByClientIdForUpdate()` to get a locked, consistent view before deciding how much to debit. Using `CreditService.getBalance()` (which uses `findByClientId` without a lock) will introduce a TOCTOU race condition.

- **Calling `CreditReservationService.debit()` with `actualAmount > reservedAmount`:** The existing implementation throws `IllegalArgumentException` at line 215 if `actualAmount > reservedAmount`. For BOOK-04/05/06, the code must call `debit(clientId, reservationId, reservedTotal)` (consuming the full reservation) and separately debit the extra via `CreditService.applyLedgerEntry`.

- **Treating `SmsFinalisedEvent.actualSegments` as the sole source of truth for per-recipient breakdown:** This field is a pre-summed total. For SEGDEV-04 (structured per-recipient breakdown), the code must load individual `SendRequestRecipient` rows.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-recipient JSONB storage | Custom text serialization / separate detail table | `@Type(JsonType.class)` from `hypersistence-utils` | Already project dependency; Hibernate maps JSONB to List/record natively; queryable |
| Platform balance lock | Manual version check / optimistic locking | `PlatformCreditBalanceRepository.findForUpdate()` | Existing pessimistic lock; already tested in Phase 19 |
| Client freeze with SMS suspension | Separate SMS bulk update | `ClientFreezeService.freeze(clientId, reason)` | Atomic freeze + SMS bulk suspend + audit event in one call |
| Platform freeze | Separate platform state update | `PlatformFreezeService.freeze(reason, shortfallAmount)` | Atomic freeze + all-client SMS suspend + audit event |
| Audit event publishing | Direct log entries | `ApplicationEventPublisher.publishEvent(new DomainAuditEvent(...))` | Consistent with all other Phase 19-21 audit patterns |

**Key insight:** All the individual primitives (reserve, debit, release, freeze) are already implemented and unit tested. Phase 22 is an orchestration layer that composes them correctly for each of the 6 scenarios.

## Common Pitfalls

### Pitfall 1: Overwriting the existing `debit()` path instead of replacing `SmsFinalisedBillingListener`

**What goes wrong:** Expanding `CreditReservationService.debit()` to handle the 5+ scenarios adds complexity to a class that should remain a single-concern atomic primitive. The decision logic belongs in a new orchestrating service.

**How to avoid:** Create `FinalBookingService` as a new class. `SmsFinalisedBillingListener` calls it. `CreditReservationService.debit()` stays unchanged.

### Pitfall 2: `debit()` throws on `actualAmount > reservedAmount`

**What goes wrong:** For BOOK-04/05/06, `actualTotal > reservedTotal`. Calling `debit(clientId, reservationId, actualTotal)` throws `IllegalArgumentException` because line 215 of `CreditReservationService` rejects amounts exceeding the reservation.

**How to avoid:** For BOOK-04/05/06, always call `debit(clientId, reservationId, reservedTotal)` (consume the full reservation), then handle the extra via `CreditService.applyLedgerEntry` with `SMS_EXTRA_DEBIT`.

**Warning signs:** `IllegalArgumentException: actualAmount X exceeds reserved amount Y` in logs during BOOK-04 scenario tests.

### Pitfall 3: `CreditService.applyLedgerEntry()` throws on negative result

**What goes wrong:** For BOOK-04, the code calls `applyLedgerEntry(clientId, SMS_EXTRA_DEBIT, -extra, ref)`. If the client's remaining balance is less than `extra`, this throws `InsufficientBalanceException` (line 116 of `CreditService`). But BOOK-05 says "client has insufficient credits" — the code must check the balance first before calling, not catch the exception.

**How to avoid:** In `FinalBookingService`, before calling `applyLedgerEntry`, acquire the client balance lock via `ClientCreditBalanceRepository.findByClientIdForUpdate()` and read the live balance to decide whether BOOK-04 or BOOK-05 applies. Then call with the correct (available) amount.

**Warning signs:** `InsufficientBalanceException` thrown instead of entering the BOOK-05 path.

### Pitfall 4: `PlatformCreditService.applyLedgerEntry()` throws on partial shortfall

**What goes wrong:** For BOOK-06, the platform balance is less than `shortfall`. Calling `applyLedgerEntry(SHORTFALL_ABSORPTION, -shortfall, ref)` throws `InsufficientPlatformBalanceException`. BOOK-06 requires absorbing only what the platform has (down to 0) and recording the unrecovered remainder.

**How to avoid:** Before calling, acquire the platform balance lock via `PlatformCreditBalanceRepository.findForUpdate()` and read the balance. If `platformBalance < shortfall`, call `applyLedgerEntry(SHORTFALL_ABSORPTION, -platformBalance, ref)` (not the full shortfall) and record `unrecovered = shortfall - platformBalance`.

**Warning signs:** `InsufficientPlatformBalanceException` in BOOK-06 path instead of partial absorption.

### Pitfall 5: BOOK-02 still has a buffer refund

**What goes wrong:** BOOK-02 says "Nexah total = expected" but `reservedTotal = rawExpectedCredits + recipientCount` (the +1 buffer). Calling `debit(clientId, reservationId, actualTotal)` where `actualTotal == rawExpectedCredits` still triggers the over-reservation refund path inside `CreditReservationService.debit()` for the buffer portion. This is **correct behavior** — the buffer is automatically refunded as an `SMS_REFUND` ledger entry. No special handling needed.

**How to avoid:** Understand that `debit(actualAmount)` where `actualAmount < reservedAmount` always refunds the difference. This is the existing implementation. No alert is created for BOOK-02 (deviation = 0).

### Pitfall 6: Transaction boundary for freeze

**What goes wrong:** `ClientFreezeService.freeze()` and `PlatformFreezeService.freeze()` are annotated `@Transactional` (propagation=REQUIRED by default). If `FinalBookingService` is also `@Transactional`, all three participate in the same transaction — the freeze and the credit settlement are atomic. This is the desired behavior.

**What goes wrong instead:** If `FinalBookingService` is not transactional, or if the freeze methods are called with `REQUIRES_NEW`, the credit settlement and freeze can commit independently, breaking atomicity.

**How to avoid:** `@Transactional` at class level on `FinalBookingService`. All callee services use `propagation=REQUIRED` (default), so they join the same transaction.

## Code Examples

Verified patterns from codebase:

### Reading client balance under lock (to support BOOK-04/05 decision)

```java
// Source: CreditReservationService.java lines 86-99 (same pattern, different use)
ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)
        .orElseThrow(() -> new ResourceNotFoundException(
                "No credit balance row for client: " + clientId,
                "client_credit_balance"));
long clientAvailable = lockRow.getBalance();
```

### Reading platform balance under lock (to support BOOK-06 decision)

```java
// Source: PlatformCreditService.applyLedgerEntry() lines 55-58
PlatformCreditBalance lockRow = balanceRepository.findForUpdate()
        .orElseThrow(() -> new ResourceNotFoundException(
                "Platform balance not initialized",
                "platform_credit_balance"));
long platformAvailable = lockRow.getBalance();
```

### Freeze client (BOOK-05)

```java
// Source: ClientFreezeService.freeze() — already complete; pass reason string
clientFreezeService.freeze(clientId, "Shortfall absorption: " + shortfallAmount + " credits");
```

### Freeze platform with shortfall amount (BOOK-06)

```java
// Source: PlatformFreezeService.freeze() — accepts nullable shortfallAmount
platformFreezeService.freeze("Platform shortfall: unrecovered=" + unrecovered, unrecovered);
```

### Apply extra client debit (BOOK-04)

```java
// Source: CreditService.applyLedgerEntry() — existing method; add SMS_EXTRA_DEBIT to LedgerEntryType
creditService.applyLedgerEntry(clientId, LedgerEntryType.SMS_EXTRA_DEBIT, -extraAmount, ref);
```

### Apply platform shortfall absorption (BOOK-05/06)

```java
// Source: PlatformCreditService.applyLedgerEntry() — SHORTFALL_ABSORPTION already in enum
platformCreditService.applyLedgerEntry(
        PlatformLedgerEntryType.SHORTFALL_ABSORPTION, -absorptionAmount, ref);
```

### JSONB column with hypersistence-utils (for SegmentDeviationAlert)

```java
// Source: Project pattern — hypersistence-utils already in pom.xml (3.9.10)
@Type(JsonType.class)
@Column(name = "per_recipient_breakdown", columnDefinition = "jsonb")
private List<RecipientDeviationEntry> perRecipientBreakdown;
```

### Publishing an audit event (for SEGMENT deviation alert)

```java
// Source: PlatformFreezeService.freeze() lines 63-68 — same pattern
eventPublisher.publishEvent(new DomainAuditEvent(
        AuditEventType.SEGMENT_DEVIATION,
        clientId,
        "system",
        "Segment deviation: sendRequestId=" + sendRequestRef + ", delta=" + delta
));
```

### Unit test structure (for FinalBookingService)

```java
// Source: SmsFinalisedBillingListenerTest.java pattern
@ExtendWith(MockitoExtension.class)
class FinalBookingServiceTest {

    @Mock private CreditReservationService creditReservationService;
    @Mock private CreditService creditService;
    @Mock private PlatformCreditService platformCreditService;
    @Mock private ClientFreezeService clientFreezeService;
    @Mock private PlatformFreezeService platformFreezeService;
    @Mock private SegmentDeviationService segmentDeviationService;
    @Mock private ClientCreditBalanceRepository clientCreditBalanceRepository;
    @Mock private SendRequestRepository sendRequestRepository;
    @Mock private SendRequestRecipientRepository recipientRepository;

    @InjectMocks
    private FinalBookingService finalBookingService;

    // One test per BOOK-0x scenario + one per SEGDEV requirement
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact on Phase 22 |
|--------------|------------------|--------------|---------------------|
| Flat `actualSegments` in `SmsFinalisedBillingListener.debit()` | Orchestrated 6-scenario booking in `FinalBookingService` | Phase 22 introduces | Listener stays thin; service owns all branching |
| No segment deviation tracking | `SegmentDeviationAlert` entity + service | Phase 22 introduces | SEGDEV-01 through SEGDEV-04 |
| `CreditReservationService.debit()` capped at `reservedAmount` | Extra debit via `SMS_EXTRA_DEBIT` ledger entry | Phase 22 adds | BOOK-04/05/06 scenarios |

**No deprecated patterns to avoid in this phase.**

## Open Questions

### 1. SmsFinalisedEvent recipient detail granularity

**What we know:** `SmsFinalisedEvent` includes `List<RecipientSummary>` but `RecipientSummary` only carries `{recipient, gatewayMessageId, finalStatus}` — no `segmentsConsumed` or `expectedSegments`. SEGDEV-03/04 need per-recipient segment breakdown.

**What's unclear:** Should `FinalBookingService` load recipients from DB (Option B, recommended) or should `SmsProviderReportListener` enrich `SmsFinalisedEvent` with segment data before publishing?

**Recommendation:** Use Option B — `FinalBookingService` loads `SendRequestRecipient` rows. This avoids changing the established `SmsFinalisedEvent` contract, keeps `SmsProviderReportListener` focused, and is safe since the booking service already needs the `SendRequest` row (for `rawExpectedCredits`, `reservedCredits`).

### 2. DeviationAlertType placement

**What we know:** The alert can be `SEGMENT` or `PLATFORM_FREEZE`. Both are from the booking phase.

**Recommendation:** Create `DeviationAlertType` enum in `billing.contract`. `SegmentDeviationAlert.alertType` references it.

### 3. Where `SendRequest.rawExpectedCredits` is obtained

**What we know:** `FinalBookingService` receives `sendRequestId` (String) and `clientId`. It must load the `SendRequest` entity to get `rawExpectedCredits` and `reservedCredits`. `SendRequestRepository.findByClientIdAndSendRequestId(clientId, sendRequestId)` is the correct method (already exists in the codebase — line 82 of `SmsService`).

**Recommendation:** Confirmed pattern. Load `SendRequest` using existing repository method. No new query needed.

### 4. `SMS_EXTRA_DEBIT` vs reusing `SMS_DEBIT`

**What we know:** `CreditReservationService.debit()` already writes `SMS_DEBIT` for the settled reservation. `CreditService.applyLedgerEntry()` is used for the extra amount. Using the same `SMS_DEBIT` type for two different credit deductions in one send request makes ledger history ambiguous.

**Recommendation:** Add `SMS_EXTRA_DEBIT` to `LedgerEntryType` for the over-reservation debit. Makes the ledger semantically clear: `SMS_RESERVATION` → `SMS_DEBIT` (settle reservation) + optional `SMS_EXTRA_DEBIT` (extra beyond buffer).

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection of all billing, freeze, SMS, and provider files listed below
- `CreditReservationService.java` — debit(), release(), reserve() implementations verified at line level
- `CreditService.java` — applyLedgerEntry() throws InsufficientBalanceException on negative balance
- `PlatformCreditService.java` — applyLedgerEntry() throws InsufficientPlatformBalanceException on negative
- `ClientFreezeService.java` — freeze(clientId, reason) signature confirmed
- `PlatformFreezeService.java` — freeze(reason, shortfallAmount) signature confirmed; shortfallAmount nullable
- `SmsFinalisedBillingListener.java` — current thin listener; Phase 22 modifies this
- `SmsProviderReportListener.java` — publishes SmsFinalisedEvent with actualSegments (summed)
- `SmsFinalisedEvent.java` — record fields confirmed; RecipientSummary has no segment data
- `NexahSmsEntry.java` — `totalSmsUnit: Integer` is the per-recipient actual segment count
- `SendRequest.java` — rawExpectedCredits, reservedCredits, reservationId, clientId all present
- `SendRequestRecipient.java` — expectedSegments, segmentsConsumed per-row fields confirmed
- `LedgerEntryType.java` — current values; SMS_EXTRA_DEBIT is not yet present
- `PlatformLedgerEntryType.java` — SHORTFALL_ABSORPTION already present
- `AuditEventType.java` — current values; SEGMENT_DEVIATION not yet present
- `PlatformFreezeState.java` — shortfall_amount column is nullable, populated by Phase 22
- Phase 21 VERIFICATION.md — confirms rawExpectedCredits/expectedSegments wired correctly
- Phase 20 VERIFICATION.md — confirms all freeze infrastructure complete and tested
- STATE.md — lock ordering decision (19-02) confirmed: client before platform

### Secondary (MEDIUM confidence)
- pom.xml (via STACK.md) — `hypersistence-utils-hibernate-63 3.9.10` confirmed available for JSONB

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all primitives exist and are tested; no new dependencies
- Architecture: HIGH — service composition pattern is identical to Phase 19/20 patterns; lock ordering is documented in codebase
- Pitfalls: HIGH — debit() cap and applyLedgerEntry() negative-balance behavior are verified from source; not assumptions
- SEGDEV schema design: HIGH — per SEGDEV-04 requirement (structured, not free text) + existing JSONB support confirmed
- `SMS_EXTRA_DEBIT` enum value: HIGH — need confirmed by reading CreditService.applyLedgerEntry; ambiguity with SMS_DEBIT justified adding new value

**Research date:** 2026-03-17
**Valid until:** 2026-04-17 (stable codebase; no external APIs involved)
