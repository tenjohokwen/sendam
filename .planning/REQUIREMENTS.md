# Requirements: Sendam v1.2 — Gateway Admin UI

**Defined:** 2026-03-12
**Core Value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**This milestone:** Admin UI for monitoring and managing the gateway

## v1 Requirements

### Foundation

- [ ] **FOUND-01**: Admin UI is built with Vue 3 Composition API (`<script setup>` exclusively), Quasar Framework, plain JavaScript (no TypeScript), primary color `#1976d2`, large components split into functional sub-components (max 250 lines)
- [ ] **FOUND-02**: UI supports English (en-US) and French (fr-FR) with full i18n key parity
- [ ] **FOUND-03**: All `Long` values from backend API are handled as `String` in the frontend to prevent JavaScript precision loss
- [ ] **FOUND-04**: All listing pages implement server-side pagination
- [ ] **FOUND-05**: Every async operation displays a loading state (`QInnerLoading` or `:loading` on buttons)
- [ ] **FOUND-06**: All API calls are centralized in an `api/` folder organized by domain
- [ ] **FOUND-07**: API errors in standard `ErrorDto` format (`helpCode`, `errorMsg.key`, `errorMsg.message`) are handled and surfaced to the user
- [ ] **FOUND-08**: All form inputs use lazy-rules validation (validate on blur)

### Dashboard

- [ ] **DASH-01**: Admin can view aggregated system stats: total active clients, total credits in system, SMS success/failure rates, delivery rate, segment totals, daily breakdown, top-up analysis, webhook delivery aggregates, provider send aggregate
- [ ] **DASH-02**: Admin can view Nexah circuit breaker state (system health indicator)

### Client Management

- [ ] **CLNT-01**: Admin can view all clients with their ID (String), label, and current available balance
- [ ] **CLNT-02**: Admin can register new clients via a creation form
- [ ] **CLNT-03**: Admin can filter/search clients by ID or name

### API Key Management

- [ ] **AKEY-01**: Admin can view all API keys associated with a specific client
- [ ] **AKEY-02**: Admin can generate a new API key for any client
- [ ] **AKEY-03**: Admin can revoke an existing API key for any client
- [ ] **AKEY-04**: Raw API key value is shown only once upon creation

### Top-up Management

- [ ] **TOUP-01**: Admin can view a list of top-up requests in `PENDING_APPROVAL` status
- [ ] **TOUP-02**: Admin can approve a top-up request, immediately crediting the client's balance
- [ ] **TOUP-03**: Admin can reject a top-up request without changing the client's balance
- [ ] **TOUP-04**: Admin can view historical top-up requests with their final status (APPROVED/REJECTED)

### SMS Monitoring

- [ ] **SMSM-01**: Admin can view a list of SMS requests in `ACCEPTED` state scheduled for future delivery
- [ ] **SMSM-02**: Admin can drill into a `sendRequestId` to view per-recipient delivery reports (DLR)

### Webhooks

- [ ] **WEBH-01**: Admin can view registered webhook URLs per client
- [ ] **WEBH-02**: Admin can monitor outgoing delivery report statuses to client webhooks (PENDING/DELIVERED/FAILED)

### UX Standards

- [ ] **UXST-01**: All success and error notifications use `$q.notify()`
- [ ] **UXST-02**: Form labels and contrast ratios meet WCAG AA standards
- [ ] **UXST-03**: Sidebar navigation for desktop; bottom tabs or burger menu for mobile
- [ ] **UXST-04**: Action buttons are disabled during loading states to prevent duplicate submissions

### Testing

- [ ] **TEST-01**: Vitest + Vue Test Utils are mandatory for all components
- [ ] **TEST-02**: One test file per component
- [ ] **TEST-03**: Tests simulate actual user flows including edge cases like network failures

## v2 Requirements

### SMS Monitoring

- **SMSM-03**: Admin can view stats on purged old records

## Out of Scope

| Feature | Reason |
|---------|--------|
| TypeScript | Explicitly excluded — plain JS only per spec |
| Multi-provider support | Only Nexah; no abstraction until second provider needed |
| Client self-service UI | Admin-only interface; no client-facing UI |

## Traceability

Which phases cover which requirements. Updated by create-roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FOUND-01 | Phase 13 | Pending |
| FOUND-02 | Phase 13 | Pending |
| FOUND-03 | Phase 13 | Pending |
| FOUND-04 | Phase 13 | Pending |
| FOUND-05 | Phase 14 | Pending |
| FOUND-06 | Phase 13 | Pending |
| FOUND-07 | Phase 13 | Pending |
| FOUND-08 | Phase 14 | Pending |
| DASH-01 | Phase 17 | Pending |
| DASH-02 | Phase 17 | Pending |
| CLNT-01 | Phase 14 | Pending |
| CLNT-02 | Phase 14 | Pending |
| CLNT-03 | Phase 14 | Pending |
| AKEY-01 | Phase 14 | Pending |
| AKEY-02 | Phase 14 | Pending |
| AKEY-03 | Phase 14 | Pending |
| AKEY-04 | Phase 14 | Pending |
| TOUP-01 | Phase 15 | Pending |
| TOUP-02 | Phase 15 | Pending |
| TOUP-03 | Phase 15 | Pending |
| TOUP-04 | Phase 15 | Pending |
| SMSM-01 | Phase 16 | Pending |
| SMSM-02 | Phase 16 | Pending |
| WEBH-01 | Phase 16 | Pending |
| WEBH-02 | Phase 16 | Pending |
| UXST-01 | Phase 14 | Pending |
| UXST-02 | Phase 13 | Pending |
| UXST-03 | Phase 13 | Pending |
| UXST-04 | Phase 14 | Pending |
| TEST-01 | Phase 18 | Pending |
| TEST-02 | Phase 18 | Pending |
| TEST-03 | Phase 18 | Pending |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-12*
*Last updated: 2026-03-12 — moved FOUND-05, FOUND-08, UXST-01, UXST-04 from Phase 13 to Phase 14 (first phase with real async operations and forms)*
