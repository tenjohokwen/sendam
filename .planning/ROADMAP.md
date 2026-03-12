# Roadmap: Sendam

## Milestones

- ✅ **v1.0 SMS Gateway** — Phases 1-7 (shipped 2026-03-11) — see `.planning/milestones/v1.0-ROADMAP.md`
- ✅ **v1.1 Operations & Observability** — Phases 8-12 (shipped 2026-03-12) — see `.planning/milestones/v1.1-ROADMAP.md`
- 🚧 **v1.2 Gateway Admin UI** — Phases 13–18 (in progress)

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

<details>
<summary>✅ v1.1 Operations & Observability (Phases 8-12) — SHIPPED 2026-03-12</summary>

- [x] Phase 8: Delivery Analytics (Admin) (1/1 plans) — completed 2026-03-11
- [x] Phase 9: Spend Reporting (Admin) (1/1 plans) — completed 2026-03-11
- [x] Phase 10: System Health (Admin) (1/1 plans) — completed 2026-03-11
- [x] Phase 11: Audit Log (3/3 plans) — completed 2026-03-11
- [x] Phase 12: Client Analytics (1/1 plans) — completed 2026-03-11

</details>

### 🚧 v1.2 Gateway Admin UI (In Progress)

**Milestone Goal:** Admin UI for monitoring and managing the SMS gateway — built with Vue 3 + Quasar, plain JS, full i18n (en-US/fr-FR).

#### Phase 13: Foundation Extension
**Goal**: Extend existing Vue 3 + Quasar scaffold with v1.2-specific infrastructure — Long→String utility, server-side pagination component, admin routing, and verified FOUND/UXST patterns throughout.
**Depends on**: Existing scaffold (Phases 1–12)
**Requirements**: FOUND-01, FOUND-02, FOUND-03, FOUND-04, FOUND-05, FOUND-06, FOUND-07, FOUND-08, UXST-01, UXST-02, UXST-03, UXST-04
**Success Criteria** (what must be TRUE):
  1. Admin section routes exist and are accessible post-login
  2. Long IDs from the backend display correctly without precision loss
  3. A reusable server-side pagination component exists and works
  4. All FOUND/UXST patterns (loading states, error handling, i18n, lazy validation, notifications, WCAG, navigation) verified in place
**Plans**: 3/3 complete

Plans:
- [x] 13-01: Long→String utility, i18n namespaces, api/ skeleton, useErrorHandler
- [x] 13-02: ServerPagination component
- [x] 13-03: Admin routing, requiresAdmin guard, 6 page stubs, sidebar navigation

#### Phase 14: Client & API Key Management
**Goal**: Admin can view, register, and search clients; manage API keys per client (view, generate, revoke, show raw key once).
**Depends on**: Phase 13
**Requirements**: CLNT-01, CLNT-02, CLNT-03, AKEY-01, AKEY-02, AKEY-03, AKEY-04
**Success Criteria** (what must be TRUE):
  1. Admin can view all clients with ID, label, and current balance
  2. Admin can register a new client via a form
  3. Admin can filter/search clients by ID or name
  4. Admin can view all API keys for a specific client
  5. Admin can generate a new API key and see the raw value exactly once
  6. Admin can revoke an existing API key
**Plans**: TBD

Plans:
- [ ] 14-01: TBD

#### Phase 15: Top-up Management
**Goal**: Admin can process the top-up approval workflow — view pending requests, approve or reject, and browse history.
**Depends on**: Phase 13
**Requirements**: TOUP-01, TOUP-02, TOUP-03, TOUP-04
**Success Criteria** (what must be TRUE):
  1. Admin can see all top-up requests in PENDING_APPROVAL status
  2. Admin can approve a request and the client's balance increases immediately
  3. Admin can reject a request with no change to the client's balance
  4. Admin can browse top-up history with final statuses (APPROVED/REJECTED)
**Plans**: 2/2 complete

Plans:
- [x] 15-01: Top-up API methods (getTopupHistory, approveTopup, rejectTopup) + i18n keys
- [x] 15-02: TopupsPage.vue — pending/history tabs, approve/reject workflow

#### Phase 16: SMS Monitoring & Webhooks
**Goal**: Admin can monitor scheduled SMS and webhook delivery — view scheduled messages, drill into per-recipient DLRs, inspect webhook config and delivery statuses.
**Depends on**: Phase 13
**Requirements**: SMSM-01, SMSM-02, WEBH-01, WEBH-02
**Success Criteria** (what must be TRUE):
  1. Admin can view SMS requests in ACCEPTED state scheduled for future delivery
  2. Admin can drill into a sendRequestId and see per-recipient delivery reports
  3. Admin can view registered webhook URLs per client
  4. Admin can monitor outgoing webhook delivery statuses (PENDING/DELIVERED/FAILED)
**Plans**: 4/4 complete

Plans:
- [x] 16-01: Backend monitor endpoints (SMS monitor + webhook monitor REST controllers)
- [x] 16-02: Frontend API methods + i18n (admin.sms + admin.webhooks sections)
- [x] 16-03: SmsMonitorPage.vue + DlrDialog.vue
- [x] 16-04: WebhooksPage.vue (two-tab: Registrations + Deliveries)

#### Phase 17: Dashboard
**Goal**: Admin has a single overview page showing aggregated system health — delivery rates, credit totals, webhook stats, provider stats, and circuit breaker state.
**Depends on**: Phases 14–16
**Requirements**: DASH-01, DASH-02
**Success Criteria** (what must be TRUE):
  1. Admin can view aggregated system stats (active clients, total credits, SMS success/failure rates, delivery rate, segment totals, daily breakdown, top-up analysis, webhook delivery aggregates, provider send aggregate)
  2. Admin can see Nexah circuit breaker state at a glance
**Plans**: TBD

Plans:
- [ ] 17-01: TBD

#### Phase 18: Testing
**Goal**: All v1.2 components have Vitest + Vue Test Utils coverage — one test file per component, simulating real user flows including edge cases.
**Depends on**: Phases 13–17
**Requirements**: TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. Every component introduced in v1.2 has a corresponding Vitest test file
  2. Tests simulate actual user flows including edge cases (e.g. network failures, validation errors)
  3. Full test suite passes cleanly
**Plans**: TBD

Plans:
- [ ] 18-01: TBD

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
| 9. Spend Reporting (Admin) | v1.1 | 1/1 | Complete | 2026-03-11 |
| 10. System Health (Admin) | v1.1 | 1/1 | Complete | 2026-03-11 |
| 11. Audit Log | v1.1 | 3/3 | Complete | 2026-03-11 |
| 12. Client Analytics | v1.1 | 1/1 | Complete | 2026-03-12 |
| 13. Foundation Extension | v1.2 | 3/3 | Complete | 2026-03-12 |
| 14. Client & API Key Management | v1.2 | 3/3 | Complete | 2026-03-12 |
| 15. Top-up Management | v1.2 | 2/2 | Complete | 2026-03-12 |
| 16. SMS Monitoring & Webhooks | v1.2 | 4/4 | Complete | 2026-03-12 |
| 17. Dashboard | v1.2 | 0/? | Not started | - |
| 18. Testing | v1.2 | 0/? | Not started | - |
