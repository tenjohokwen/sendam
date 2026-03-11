# Roadmap: Sendam

## Milestones

- ✅ **v1.0 SMS Gateway** — Phases 1-7 (shipped 2026-03-11) — see `.planning/milestones/v1.0-ROADMAP.md`
- 🚧 **v1.1 Operations & Observability** — Phases 8-12 (in progress)

## Phases

<details>
<summary>✅ v1.0 SMS Gateway (Phases 1-7) — SHIPPED 2026-03-11</summary>

- [x] Phase 1: Client & API Key Authentication (3/3 plans) — completed 2026-03-10
- [x] Phase 2: Credit Ledger & Top-Ups (3/3 plans) — completed 2026-03-10
- [x] Phase 3: Send SMS & Credit Reservation (4/4 plans) — completed 2026-03-10
- [x] Phase 4: Provider Integration & Message Status (3/3 plans) — completed 2026-03-10
- [x] Phase 5: Webhooks (2/2 plans) — completed 2026-03-11
- [x] Phase 6: Fix API Key Security Chain (2/2 plans) — completed 2026-03-11
- [x] Phase 7: Fix Sender ID Forwarding (1/1 plan) — completed 2026-03-11

</details>

### 🚧 v1.1 Operations & Observability (In Progress)

**Milestone Goal:** Admin-facing operational visibility — what was sent, what was spent, what happened, and how the system is behaving. Plus client-facing self-service analytics.

#### Phase 8: Delivery Analytics (Admin)
**Goal**: Admin can query SMS delivery outcomes with filters and daily breakdown
**Depends on**: Nothing (read-only queries on existing sms tables)
**Requirements**: DANL-01, DANL-02, DANL-03
**Success Criteria** (what must be TRUE):
  1. Admin can query sent / delivered / failed counts and delivery rate, with optional filter by client and time period
  2. Segment totals are included alongside message counts
  3. Response includes a daily breakdown (counts per day) within the filtered window
**Plans**: 1/1 complete

Plans:
- [x] 08-01: Delivery analytics data layer + admin REST endpoint — completed 2026-03-11

#### Phase 9: Spend Reporting (Admin)
**Goal**: Admin can query credit consumption and top-up history per client
**Depends on**: Nothing (read-only queries on existing ledger_entry table)
**Requirements**: SPEN-01, SPEN-02, SPEN-03
**Success Criteria** (what must be TRUE):
  1. Admin can query net credits consumed (debits minus refunds) per client for a given period
  2. Response includes breakdown by ledger entry type (SMS_DEBIT, SMS_REFUND, TOPUP_APPROVED, etc.)
  3. Admin can view top-up history (pending / approved / rejected) per client per period
**Plans**: TBD

Plans:
- [ ] 09-01: TBD

#### Phase 10: System Health (Admin)
**Goal**: Admin can query real-time health metrics for the platform's critical subsystems
**Depends on**: Nothing (reads Resilience4j state and existing webhook/sms tables)
**Requirements**: HLTH-01, HLTH-02, HLTH-03
**Success Criteria** (what must be TRUE):
  1. Admin can query the current Nexah circuit breaker state (CLOSED / OPEN / HALF_OPEN)
  2. Admin can query webhook delivery stats — total attempts, failure count, EXHAUSTED count
  3. Admin can query provider send stats — SMS submissions to Nexah, DR callbacks received, failure rate
**Plans**: TBD

Plans:
- [ ] 10-01: TBD

#### Phase 11: Audit Log
**Goal**: All significant platform events are recorded and queryable by admin
**Depends on**: Nothing (adds new table; hooks into existing service calls)
**Requirements**: AUDT-01, AUDT-02, AUDT-03, AUDT-04, AUDT-05
**Success Criteria** (what must be TRUE):
  1. Admin actions on clients (creation, top-up decisions, API key ops) are automatically recorded
  2. Client API key operations (create / revoke) are automatically recorded
  3. Every SMS send request submission is automatically recorded (client, recipient count, timestamp)
  4. Webhook config changes (register / update / delete) are automatically recorded
  5. Admin can query the audit log, paginated, with optional filter by client and time period
**Plans**: TBD

Plans:
- [ ] 11-01: TBD

#### Phase 12: Client Analytics
**Goal**: Clients can query their own delivery stats and credit consumption
**Depends on**: Phase 8 (shares query patterns with admin delivery analytics)
**Requirements**: CANL-01, CANL-02, CANL-03
**Success Criteria** (what must be TRUE):
  1. Client can query their own delivery stats (sent / delivered / failed + delivery rate) filtered by time period
  2. Client can query their own billed segment totals for a time period
  3. Client can query their own net credit consumption for a time period
**Plans**: TBD

Plans:
- [ ] 12-01: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Client & API Key Auth | v1.0 | 3/3 | Complete | 2026-03-10 |
| 2. Credit Ledger & Top-Ups | v1.0 | 3/3 | Complete | 2026-03-10 |
| 3. Send SMS | v1.0 | 4/4 | Complete | 2026-03-10 |
| 4. Provider Integration | v1.0 | 3/3 | Complete | 2026-03-10 |
| 5. Webhooks | v1.0 | 2/2 | Complete | 2026-03-11 |
| 6. Fix API Key Security Chain | v1.0 | 2/2 | Complete | 2026-03-11 |
| 7. Fix Sender ID Forwarding | v1.0 | 1/1 | Complete | 2026-03-11 |
| 8. Delivery Analytics (Admin) | v1.1 | 1/1 | Complete | 2026-03-11 |
| 9. Spend Reporting (Admin) | v1.1 | 0/? | Not started | - |
| 10. System Health (Admin) | v1.1 | 0/? | Not started | - |
| 11. Audit Log | v1.1 | 0/? | Not started | - |
| 12. Client Analytics | v1.1 | 0/? | Not started | - |
