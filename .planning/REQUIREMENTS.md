# Requirements: Sendam v1.3 — Provider Integrity & Platform Credit Account

**Defined:** 2026-03-17
**Core Value:** Clients can send SMS and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.

---

## v1 Requirements

### Platform Credit Account (PLAT)

- [ ] **PLAT-01**: Admin can record a Nexah credit purchase, specifying the amount purchased; platform balance increases by that amount and a ledger entry is written
- [ ] **PLAT-02**: Platform balance has an append-only ledger; each entry records: entry type, amount (signed), balance_after, reference, and timestamp
- [ ] **PLAT-03**: Platform ledger distinguishes between entry types: `NEXAH_PURCHASE` (credits bought from Nexah), `TOPUP_DEBIT` (client top-up approved), and `SHORTFALL_ABSORPTION` (client overdraft absorbed by platform)
- [ ] **PLAT-04**: Admin can query the current platform balance
- [ ] **PLAT-05**: Admin can query the platform balance ledger history, paginated and filterable by entry type
- [ ] **PLAT-06**: Approving a client top-up debits the platform balance by the approved amount; the debit is atomic with the client credit
- [ ] **PLAT-07**: Client top-up approval is rejected if the platform balance would go negative

### Credit Reservation (RESV)

- [ ] **RESV-01**: Before sending to Nexah, Sendam calculates expected segment count per recipient using the standard SMS segment formula (GSM-7 vs UCS-2 encoding, 160/153 and 70/67 character thresholds)
- [ ] **RESV-02**: Reservation amount = `(calculated_segments + 1) × recipient_count` — the +1 buffer per recipient guards against Nexah reporting a higher count than Sendam expects
- [ ] **RESV-03**: The raw expected amount without buffer (`calculated_segments × recipient_count`) is stored alongside the reservation for deviation comparison
- [ ] **RESV-04**: Per-recipient expected segment count is stored (not just the total) to enable per-recipient breakdown in deviation alerts

### Final Booking on Nexah Send Response (BOOK)

Final booking uses `total_sms_unit` per recipient from the Nexah send response. Five scenarios:

- [ ] **BOOK-01** *(Nexah total < expected)*: Book actual amount, refund excess reservation to client, record a `SEGMENT` deviation alert
- [ ] **BOOK-02** *(Nexah total = expected)*: Book actual amount, refund excess reservation to client, no deviation alert raised
- [ ] **BOOK-03** *(Nexah total > expected but ≤ reserved)*: Book actual amount, refund remaining reservation to client, record a `SEGMENT` deviation alert
- [ ] **BOOK-04** *(Nexah total > reserved, client has sufficient credits)*: Book actual amount (extra deducted from client beyond the reservation), record a `SEGMENT` deviation alert
- [ ] **BOOK-05** *(Nexah total > reserved, client has insufficient credits)*: Deduct all remaining client credits; remainder absorbed from platform balance via a `SHORTFALL_ABSORPTION` ledger entry; freeze client account; record a `SEGMENT` deviation alert including the shortfall amount and the freeze
- [ ] **BOOK-06** *(BOOK-05 but platform balance also insufficient)*: Absorb as much as the platform balance allows (down to 0); record the unrecovered remainder; freeze the entire platform; record a `PLATFORM_FREEZE` deviation alert with full shortfall detail

### Client Account Freeze (CFREEZE)

- [ ] **CFREEZE-01**: A frozen client account rejects all new credit reservations (SMS send requests are rejected at the reservation step)
- [ ] **CFREEZE-02**: All pending scheduled SMS for a frozen client are suspended (not cancelled — they retain their schedule)
- [ ] **CFREEZE-03**: Freeze reason and timestamp are recorded on the client account record
- [ ] **CFREEZE-04**: Admin can unfreeze a client account with a mandatory resolution note; suspended scheduled SMS resume upon unfreeze
- [ ] **CFREEZE-05**: In-flight sends (already submitted to Nexah, DR not yet received) at freeze time complete normally — their booking is processed when the response/DR arrives

### Platform Freeze (PFLAT)

- [ ] **PFLAT-01**: A platform freeze blocks all new credit reservations across all clients (all SMS send requests rejected)
- [ ] **PFLAT-02**: All pending scheduled SMS across all clients are suspended on platform freeze
- [ ] **PFLAT-03**: Platform freeze reason, unrecovered shortfall amount, and timestamp are recorded
- [ ] **PFLAT-04**: In-flight sends at freeze time complete normally — their bookings are processed when responses/DRs arrive
- [ ] **PFLAT-05**: Admin explicitly lifts the platform freeze with a mandatory resolution note; all suspended scheduled SMS resume upon unfreeze

### Segment Deviation Detection (SEGDEV)

- [ ] **SEGDEV-01**: On Nexah send response, Sendam computes the deviation: `actual_total (from Nexah) − expected_total (stored at reservation time)`
- [ ] **SEGDEV-02**: Any non-zero deviation (positive or negative) produces exactly one `SEGMENT` deviation alert per send request
- [ ] **SEGDEV-03**: The `SEGMENT` alert contains the full picture: sendRequestId, client, total expected segments, total actual segments, total delta, per-recipient breakdown (recipient number, expected segments, actual segments, per-recipient delta), financial action taken (amount refunded / extra debited / shortfall absorbed / freeze triggered), timestamp
- [ ] **SEGDEV-04**: Per-recipient breakdown is stored as structured data (not free text) so it can be queried

### Periodic Balance Reconciliation (BALREC)

- [ ] **BALREC-01**: A scheduled job polls Nexah `/smscredit` at a configurable interval (default: 15 minutes)
- [ ] **BALREC-02**: The job compares Nexah's reported credit balance against Sendam's tracked platform balance
- [ ] **BALREC-03**: On mismatch, a `BALANCE` deviation alert is recorded: Nexah-reported balance, Sendam-tracked balance, delta, timestamp, status `OPEN`
- [ ] **BALREC-04**: The polling interval is configurable via application properties (`sendam.reconciliation.interval-minutes`)

### Deviation Alert Management (DEVMGMT)

- [ ] **DEVMGMT-01**: Admin can list deviation alerts, paginated, filterable by type (`SEGMENT` / `BALANCE` / `PLATFORM_FREEZE`) and status (`OPEN` / `ACKNOWLEDGED` / `RESOLVED`)
- [ ] **DEVMGMT-02**: Each alert exposes all structured detail: type, status, delta, financial impact, timestamp, and for `SEGMENT` alerts the full per-recipient breakdown
- [ ] **DEVMGMT-03**: Admin can acknowledge an alert with a mandatory free-text note; status → `ACKNOWLEDGED`
- [ ] **DEVMGMT-04**: Admin can resolve an alert with a mandatory free-text note; status → `RESOLVED`
- [ ] **DEVMGMT-05**: Each alert retains a full immutable audit trail: original deviation data, all status transitions with timestamps, and all admin notes

---

## v2 Requirements

### Notifications

- **NOTIF-01**: Admin receives an email notification when a new deviation alert is created — in v1.3 admin polls the API
- **NOTIF-02**: Configurable deviation threshold for `SEGMENT` alerts — only alert if `|delta| > N` credits to reduce noise from minor rounding differences

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| Automatic deviation resolution | Deviations require human judgement and evidence before Nexah reconciliation |
| Client-facing deviation visibility | Internal operational concern; client billing is always finalized using Nexah's actual figures |
| Platform balance overwrite from Nexah poll | Reconciliation is advisory; Sendam's ledger is the source of truth |
| Nexah credit purchase automation | Sendam does not control the Nexah account programmatically |
| Cancelling in-flight sends on freeze | Sends already submitted to Nexah cannot be recalled |

---

## Traceability

*Populated by `/gsd:create-roadmap`*

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAT-01 – PLAT-07 | — | Pending |
| RESV-01 – RESV-04 | — | Pending |
| BOOK-01 – BOOK-06 | — | Pending |
| CFREEZE-01 – CFREEZE-05 | — | Pending |
| PFLAT-01 – PFLAT-05 | — | Pending |
| SEGDEV-01 – SEGDEV-04 | — | Pending |
| BALREC-01 – BALREC-04 | — | Pending |
| DEVMGMT-01 – DEVMGMT-05 | — | Pending |

**Coverage:**
- v1 requirements: 34 total across 8 categories
- Mapped to phases: 0 (pending roadmap)
- Unmapped: 34 ⚠️

---
*Requirements defined: 2026-03-17*
*Last updated: 2026-03-17 after initial definition*
