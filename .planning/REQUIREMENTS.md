# Requirements: Sendam v1.1

**Defined:** 2026-03-11
**Core Value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.

## v1.1 Requirements

### Delivery Analytics (Admin)

- [x] **DANL-01**: Admin can query delivery stats (sent / delivered / failed counts + delivery rate) with optional filter by client and optional filter by time period
- [x] **DANL-02**: Admin can query billed segment totals with optional filter by client and time period
- [x] **DANL-03**: Delivery stats response includes both overall summary totals and a time-bucketed daily breakdown

### Spend Reporting (Admin)

- [x] **SPEN-01**: Admin can query net credits consumed (debits minus refunds) per client per time period
- [x] **SPEN-02**: Spend response includes breakdown by ledger entry type (SMS_DEBIT, SMS_REFUND, TOPUP_APPROVED, etc.)
- [x] **SPEN-03**: Admin can query top-up history (pending / approved / rejected) per client per time period

### System Health (Admin)

- [x] **HLTH-01**: Admin can query the current Nexah circuit breaker state (CLOSED / OPEN / HALF_OPEN)
- [x] **HLTH-02**: Admin can query webhook delivery stats (total attempts, failure count, EXHAUSTED count)
- [x] **HLTH-03**: Admin can query provider stats (SMS submissions sent to Nexah, DR callbacks received, failure rate)

### Audit Log (Admin)

- [ ] **AUDT-01**: System records admin actions on clients: client creation, top-up approval/rejection, API key ops performed by admin
- [ ] **AUDT-02**: System records client API key ops: client creating or revoking their own API keys
- [ ] **AUDT-03**: System records every SMS send request submission (client, recipient count, timestamp)
- [ ] **AUDT-04**: System records webhook config changes: registration, update, deletion
- [ ] **AUDT-05**: Admin can query audit log, paginated, with optional filter by client and time period

### Client Analytics

- [ ] **CANL-01**: Client can query their own delivery stats (sent / delivered / failed + delivery rate) filterable by time period
- [ ] **CANL-02**: Client can query their own billed segment totals for a time period
- [ ] **CANL-03**: Client can query their own net credit consumption for a time period

## v2 Requirements

(None identified — all discussed features committed to v1.1)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Client access to audit log | Admin-only in v1.1; if needed, add in v1.2 |
| Real-time / push analytics | REST query only; adds significant complexity |
| Pre-aggregated materialized tables | Live queries first; optimize later if performance requires it |
| Time-bucketed breakdown for client analytics | Clients get summary totals only; admin gets daily breakdowns |
| Client-facing spend breakdown by entry type | Client needs net total, not entry-level detail |

## Traceability

Which phases cover which requirements. Updated by `/gsd:create-roadmap`.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DANL-01 | Phase 8 | Complete |
| DANL-02 | Phase 8 | Complete |
| DANL-03 | Phase 8 | Complete |
| SPEN-01 | Phase 9 | Complete |
| SPEN-02 | Phase 9 | Complete |
| SPEN-03 | Phase 9 | Complete |
| HLTH-01 | Phase 10 | Complete |
| HLTH-02 | Phase 10 | Complete |
| HLTH-03 | Phase 10 | Complete |
| AUDT-01 | Phase 11 | Pending |
| AUDT-02 | Phase 11 | Pending |
| AUDT-03 | Phase 11 | Pending |
| AUDT-04 | Phase 11 | Pending |
| AUDT-05 | Phase 11 | Pending |
| CANL-01 | Phase 12 | Pending |
| CANL-02 | Phase 12 | Pending |
| CANL-03 | Phase 12 | Pending |

**Coverage:**
- v1.1 requirements: 17 total
- Mapped to phases: 17
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-11*
*Last updated: 2026-03-11 after roadmap creation*
