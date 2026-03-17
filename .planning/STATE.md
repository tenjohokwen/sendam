# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-17)

**Core value:** Clients can send SMS messages and trust that billing is exact, idempotent, and auditable — credits are never silently lost or incorrectly charged.
**Current focus:** v1.3 COMPLETE — Phase 25: SMS Billing Finalization Hardening (in progress)

## Current Position

Phase: 25 of 25 (SMS Billing Finalization Hardening)
Plan: 1 of 1
Status: Phase in progress
Last activity: 2026-03-17 — Completed 25-01-PLAN.md: SmsFinalisedEvent.actualSegments → primitive long; SmsSchedulerService.forceFinalize() publishes SmsFinalisedEvent(0L) to release reservation; InsufficientPlatformBalanceException Javadoc HTTP 422 typo fixed; 213 tests pass

Progress: v1.0 COMPLETE | v1.1 COMPLETE | v1.2 COMPLETE | v1.3 COMPLETE | v1.4 in progress ████████████████████████░ 96%

## Accumulated Context

### Decisions

All v1.0 and v1.1 decisions are logged in PROJECT.md Key Decisions table and archived in:
- `.planning/milestones/v1.0-ROADMAP.md`
- `.planning/milestones/v1.1-ROADMAP.md`

**13-01 decisions:**
- FOUND-06 pattern: all API calls centralized in `api/<domain>/` folders; no direct axios in components/composables
- FOUND-07 pattern: admin pages import `useErrorHandler` exclusively for API error handling; existing reactive implementation (setError/clearError + computed + i18n) preserved over simpler spec
- Long ID guard: always call `longToString()` on backend ID fields before display (Java BIGSERIAL exceeds JS MAX_SAFE_INTEGER)

**13-02 decisions:**
- ServerPagination pattern: `:total-elements :total-pages :page-size v-model @page-change` — component handles 1-to-0-based conversion internally; callers pass 0-based index directly to `?page=` query param
- v-if (not v-show) on ServerPagination outer wrapper — component truly unmounts when totalPages <= 1

**13-03 decisions:**
- Admin route group: parent sets `requiresAuth: true, requiresAdmin: true`; children use lazy imports; `requiresAdmin` guard defers full role check to Phase 14 (no Pinia user store yet; backend 403 is the real security boundary)
- Admin sidebar nav section: no `v-if="isAdmin"` in this plan — visible to all authenticated users until Phase 14 adds the user store and role-based conditional rendering
- Admin page stubs use static `"Loading..."` string (not i18n key) — placeholder is replaced entirely when Phase 14-17 build real content

**14-01 decisions:**
- UserDto.authorities (Set<String>) is the role field; store named `authorities` to match; values are strings like `"ROLE_ADMIN"`
- profileApi import path: `src/api/profile.api` (flat file, no subfolder)
- beforeEach guard made async to support `await userStore.fetchUser()`
- userStore.reset() called on logout in MainLayout — clears isAdmin immediately without page reload
- isLoaded guard pattern: check `userStore.isLoaded` before fetchUser() to avoid duplicate profile API calls per navigation

**14-02 decisions:**
- statusLabelMap pattern: `{ ACTIVE: () => t('...'), INACTIVE: ... }` map with getter functions for enum-to-i18n labels — reactive, avoids string manipulation
- openManageKeys stub: sets showApiKeysDialog=true but no ApiKeysDialog yet — Plan 03 completes the wiring
- No raw key shown after createClient — backend POST /api/admin/clients returns rawApiKey: null; admin must generate key explicitly in API Keys panel

**14-03 decisions:**
- show-once credential pattern: v-if on RawKeyDialog inside ApiKeysDialog + null rawKeyResult.value in onRawKeyDialogClose — raw key dropped from memory (AKEY-04)
- v-if (not v-show) on ApiKeysDialog in ClientsPage — all key state released on close; selectedClient cleared on close
- per-row loading map: isRevoking = ref({}) keyed by keyId string — independent per-row loading without shared boolean

**15-01 decisions:**
- getTopupHistory accepts params={} default — all filters (topupStatus, clientId, from, to) are optional; callers omit arg for unfiltered results
- topupId for approveTopup/rejectTopup MUST be "top_XXX" format — raw numeric id returns 404; Plan 02 must construct "top_" + item.id after fetch
- topupAlreadyProcessed i18n key added for 409 TOPUP_ALREADY_PROCESSED — prevents raw English backend message leaking through useErrorHandler fallback

**15-02 decisions:**
- topupId constructed as 'top_' + item.id before normalizeLongIds spread — explicit ordering to preserve numeric value for string prefix
- Both Approve and Reject buttons disabled when either isApproving[topupId] or isRejecting[topupId] truthy — prevents double-action per row
- History tab shows item.id (string-normalized) without top_ prefix — prefix only needed for PUT API calls, not display

**16-01 decisions:**
- ADMIN_SMS_MONITOR = /api/admin/sms/monitor/** (not /api/admin/sms/**) — preserves sibling ADMIN_ANALYTICS at /api/admin/sms/analytics/**
- findBySendRequestId(String) derived query added to SendRequestRepository — AdminSmsMonitorService needs String→Long PK resolution for DLR lookup
- WebhookEndpointRow.status uses EntityStatus.name() — WebhookEndpoint has no separate WebhookStatus field, EntityStatus is the lifecycle status

**16-02 decisions:**
- admin.sms has 24 keys and admin.webhooks has 30 keys — plan stated 20/22 but those were undercount of the actual key spec; actual key list is authoritative
- adminApi extension pattern: new methods appended after last existing method with TICKET-ID comment annotations

**16-03 decisions:**
- showDlrDialog + selectedSendRequestId pair: two separate refs — dialog opens via showDlrDialog=true, DlrDialog loads when sendRequestId non-null; onPageChange resets selectedSendRequestId=null to prevent stale DLR data
- DlrDialog.vue is maximized q-dialog — full-screen gives most usable space for DLR recipient tables
- Dialog drill-down pattern: parent page manages showDialog + selectedId; child dialog watches prop with immediate to auto-load on open

**16-04 decisions:**
- events column omitted from WebhooksPage Registrations table — removed as prescribed fallback to stay under 250-line limit (234 total)
- deliveryStatus uses binary positive/negative badge; attemptStatus uses named attemptStatusColorMap — distinct semantics kept visually separate

**17-01 decisions:**
- dashboard section inserted after webhooks inside admin i18n object — natural placement after last existing admin subsection
- getCircuitBreakerHealth/getProviderStats/getWebhookHealth take no params — backend endpoints accept no query parameters
- Actual dashboard key count is 32 (not 37 as plan estimate) — key spec body is authoritative

**17-02 decisions:**
- Three separate card components (SmsCard, BillingCard, SystemCard) enforced by 250-line page limit — decomposition is mandatory
- cbColorMap { CLOSED: 'positive', HALF_OPEN: 'warning', OPEN: 'negative' } defined in DashboardSystemCard — card owns its health display logic
- formatPct(rate) local to DashboardSmsCard — not promoted to shared utility; used only in this one card

**17-03 decisions:**
- normalizeLongIds applied only to clients array (each client's id field); never applied to numeric stat/health aggregates
- activeClientCount and totalCredits derived client-side from clients.value — no dedicated aggregate endpoint needed
- useErrorHandler pattern: hasError/errorMessage/setError/clearError — consistent with ClientsPage and WebhooksPage

**18-04 decisions:**
- Tab switch tested via wrapper.vm.activeTab = 'tab-name' — jsdom q-tab clicks don't reliably trigger Quasar panel routing; direct ref mutation fires the watch watcher correctly
- onFilterChange() called directly via wrapper.vm for WebhooksPage filter test — equivalent to q-select @update:model-value without jsdom select interaction complexity
- QInnerLoading PascalCase in AdminDashboardPage.vue produces Vue resolution warning in jsdom (cosmetic only, all tests pass) — other pages use kebab q-inner-loading which resolves cleanly
- mockResolvedValueOnce chaining needed for TopupsPage approve/reject: handler calls loadPending() after success requiring a second mock for the reload

**18-03 decisions:**
- Quasar q-dialog teleports content to document.body — wrapper.find() fails; use document.querySelector for physical DOM assertions; wrapper.findComponent() still finds virtual tree components
- ApiKeysDialog watch-without-immediate: mount with modelValue=false then setProps to trigger watcher; mounting open=true does not fire the watcher
- DlrDialog watch-immediate: mock getDlrForRequest BEFORE mount when sendRequestId is non-null; immediate watcher fires synchronously during component setup
- DlrDialog pagination: onPageChange(N) sets currentPage=N, loadDlr uses currentPage-1; test calls onPageChange(2) to expect API page=1

**18-01 decisions:**
- Manual npm install used instead of quasar ext add — avoids interactive prompts, exact version control
- passWithNoTests: true in vitest.config.mjs — Vitest 4 exits code 1 on no files; option added for CI safety
- sassVariables: false in quasar vite plugin — no sass processing needed in jsdom test environment
- Global axios boot mock in setup-file.js blocks #q-app/wrappers virtual module before any test runs

**18-02 decisions:**
- mount (not shallowMount) for Quasar display cards — shallowMount stubs q-card-section; wrapper.text() returns empty string
- DashboardSystemCard cbStateLabel verified via wrapper.text().toContain() — more resilient than QBadge stub attribute inspection
- @quasar/quasar-app-extension-testing-unit-vitest installed with --legacy-peer-deps to resolve peer conflict

**19-01 decisions:**
- Singleton row uses id=1 in Flyway INSERT — TSID-generated IDs encode timestamp bits at high-bit values far above 1; safe in practice. findForUpdate() uses no WHERE clause so the id value is never referenced by application code.
- PlatformLedgerEntryType created in Plan 01 alongside the entity to avoid compile errors — Plan 02 must NOT recreate it
- findByOptionalType uses a single JPQL optional-filter query: WHERE (:type IS NULL OR e.entryType = :type) — single method covers filtered and unfiltered cases

**19-02 decisions:**
- applyLedgerEntry has no @Transactional annotation — class-level @Transactional covers it; propagation=REQUIRED means callers (TopupService Plan 04) propagate their own transaction so topup row + client credit + platform balance all commit/rollback atomically
- InsufficientPlatformBalanceException does not carry clientId — platform balance is not per-client; currentBalance and requestedAmount are the diagnostic fields
- PlatformLedgerEntryDto uses @JsonProperty("balance_after") — mirrors LedgerEntryDto snake_case convention for consistent API response shape
- Lock order documented in applyLedgerEntry comment: (1) topup row [caller], (2) client credit balance [CreditService], (3) platform balance [PlatformCreditService] — Plan 04 must call in this order

**19-03 decisions:**
- AdminPlatformCreditResource uses class-level @PreAuthorize("hasRole('ADMIN')") — all three endpoints share the same authority; prevents accidental omission on future additions to the controller
- HTTP 422 (UNPROCESSABLE_ENTITY) for InsufficientPlatformBalanceException — distinguishes platform balance shortfall (422) from client balance shortfall (400, InsufficientBalanceException); callers can programmatically differentiate
- TopupService.approve() three-lock atomic sequence is now complete: topup row (findByIdForUpdate) → client credit (CreditService.applyLedgerEntry) → platform balance (PlatformCreditService.applyLedgerEntry); all in one @Transactional propagated from TopupService.approve()

**20-01 decisions:**
- PlatformFreezeState.shortfall_amount is nullable — manually-initiated admin freezes have no shortfall; only Phase 22 PFLAT-03 (automatic low-balance trigger) populates this field
- SUSPENDED inserted between ACCEPTED and SUBMITTED in SendRequestStatus — new constant is backward-compatible; unfreezing restores status to ACCEPTED
- PlatformFreezeStateRepository uses two-method pattern: findState() (non-locking read) and findForUpdate() (pessimistic write lock for transitions) — mirrors PlatformCreditBalanceRepository
- Fully-qualified enum class name in JPQL bulk queries (com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED) — avoids import ambiguity in @Query strings

**20-02 decisions:**
- Cross-module repo dependency: ClientFreezeService (account.service) injects SendRequestRepository (sms.repo) directly — freeze service is a domain orchestrator needing atomic SMS bulk-update; documented in class comment
- PlatformFreezeService lives in billing.service — keeps CreditReservationService (also billing) calling isFrozen() within the same package without cross-module service dependency
- isFrozen() uses @Transactional(readOnly=true) method-level override — class-level @Transactional is readWrite; method-level narrows to read for non-locking state checks

**21-01 decisions:**
- No @Builder.Default on rawExpectedCredits/expectedSegments — 0 is Java's natural primitive default; omitting @Builder.Default keeps Lombok @SuperBuilder chain clean (contrast with @Builder.Default on sendStatus enum fields which require explicit initializer)
- DEFAULT 0 pattern for NOT NULL numeric ALTER TABLE columns — mirrors V12 DEFAULT FALSE pattern for boolean columns; satisfies NOT NULL constraint for existing rows without a data migration

**21-02 decisions:**
- RESV buffer formula: reservationAmount = (long)(expectedSegments + 1) * recipientCount — canonical form; rawExpectedCredits and reservedCredits stored separately for Phase 22 deviation detection
- segmentCount field on SendRequest set to expectedSegments (same formula, aligned naming) — semantics unchanged (per-message segment count, not total)
- BalanceResponse constructor in tests: (long availableBalance, String unit, String currency, Instant lastUpdatedAt) — plan template assumed different signature; adapted to actual code

**22-01 decisions:**
- BIGINT PRIMARY KEY (not BIGSERIAL) for V14 migration — all project tables use TSID-generated IDs via @Tsid; BIGSERIAL would conflict with ID generation strategy
- RecipientDeviationEntry as inner record inside SegmentDeviationAlert — co-located with entity; no separate file; Plan 02 references as SegmentDeviationAlert.RecipientDeviationEntry
- SegmentDeviationAlertRepository minimal stub — Plan 02 only needs save(); Phase 24 adds query methods for admin deviation alert listing

**22-03 decisions:**
- @MockitoSettings(LENIENT) on test class when @BeforeEach stubs are not consumed by all tests (early-return paths like zero-segments and exact-match BOOK-02 do not reach all stubs)
- Mockito.mock(Entity.class) for entities with @Tsid id — builder cannot set id at test time; when(entity.getId()).thenReturn(pk) is required pattern

**23-01 decisions:**
- BillingConfig has no @EnableScheduling — ClientConfig (sms.config) already enables scheduling; duplicate adds confusion without benefit
- fetchCreditBalance() reads top-level credit field (not balance[].credit array) — top-level is total across all countries; consistent with checkAvailability() logic
- BalanceDeviationAlertRepository is a minimal stub (save() only) — Phase 24 adds query methods for admin listing/filtering
- billing.config package created for billing module configuration, mirroring sms.config and account.config conventions

**23-02 decisions:**
- BalanceReconciliationJob has no class-level @Transactional — the Nexah HTTP call must not hold a DB connection; mirrors SegmentDeviationService split pattern from Phase 22
- Silent skip on Nexah exception: catch any Exception (not just ProviderUnavailableException); log.warn + return, no re-throw, no alert
- platformCreditService.getBalance() short-circuits on Nexah failure — no point reading Sendam balance if Nexah balance is unavailable

**24-01 decisions:**
- DeviationAlertEvent extends BaseEntity (not AbstractAuditingEntity) — owns acted_at/acted_by; Spring Security auditing columns not applicable to admin-action events
- Nullable FK pattern: segmentAlertIdFk null for BALANCE rows; balanceAlertIdFk null for SEGMENT/PLATFORM_FREEZE rows — single table for all alert event types avoids per-alert-type event tables
- AlertStatus.canTransitionTo() uses Java 17 switch expression with no default case — compiler forces update of transition rules when new enum constant added

**24-02 decisions:**
- BalanceDeviationAlertRepository.findByOptionalFilters omits type param — BALANCE table only contains type=BALANCE; service layer (Plan 03) handles routing; never passes type filter to balance repo
- DeviationAlertDto uses Long (boxed) for type-specific numerics (nexahBalance, sendamBalance, shortfallAmount, unrecoveredAmount) — null signals field not applicable for alert type; primitive long only for delta which is guaranteed on all types
- RecipientBreakdownDto as inner record of DeviationAlertDto — co-located with owning DTO; referenced as DeviationAlertDto.RecipientBreakdownDto by callers

**24-03 decisions:**
- guardTransition helper extracted: shared by acknowledge and resolve; throws AlertStatusTransitionException(message, alertId, currentStatus) before any write
- saveEvent centralises DeviationAlertEvent construction: balanceAlertIdFk and segmentAlertIdFk are explicit nullable params; exactly one is non-null per call — enforces single-FK contract at call site
- Comparator.nullsLast(reverseOrder()) for merge sort in listAlerts null-type path — defensive for theoretical null createdDate edge case

**24-04 decisions:**
- ADMIN_DEVIATION_ALERTS = /api/admin/deviations/** added as 17th entry in SECURED_MAPPINGS — Map.ofEntries has no entry limit (unlike Map.of); plan note about hard limit does not apply
- AdminDeviationAlertResource is a pure thin delegate: all four methods are single-line delegations to DeviationAlertManagementService; no business logic in controller layer

**25-01 decisions:**
- SmsFinalisedEvent.actualSegments changed to primitive long — all existing call sites use long-compatible literals; no callers required change
- forceFinalize() always publishes SmsFinalisedEvent with 0L actualSegments (never the estimated value) — zero-segments path in FinalBookingService.book() calls CreditReservationService.release() and returns; correct treatment for a timeout with no DLR
- SmsSchedulerServiceTest verifies only event publication, not billing chain — billing chain has its own unit tests; stale SendRequest reservationId=null is handled by FinalBookingService null-guard early return

**22-02 decisions:**
- BOOK-06 issues two alerts (SEGMENT + PLATFORM_FREEZE) in one transaction — SEGMENT records the client's deviation; PLATFORM_FREEZE records the platform-level incident for operator investigation
- Client balance lock acquired before creditReservationService.debit() in BOOK-04/05/06 — ensures clientAvailable read is atomic with subsequent debit; re-entrant lock within same transaction is safe on PostgreSQL
- platformAvailable > 0 guard before BOOK-06 partial absorption — prevents applyLedgerEntry(-0) call; mirrors clientAvailable > 0 guard for drain step
- SegmentDeviationAlertData record defined inside SegmentDeviationService — data transfer object co-located with the service that owns it; FinalBookingService imports via inner class reference

**20-03 decisions:**
- AdminClientFreezeResource uses class-level @PreAuthorize("hasRole('ADMIN')") — consistent with AdminPlatformCreditResource pattern (19-03 precedent)
- ADMIN_CLIENT_FREEZE = /api/admin/clients/*/freeze/** added alongside existing ADMIN_CLIENTS — belt-and-suspenders specificity; same pattern as ADMIN_API_KEYS alongside ADMIN_CLIENTS
- CreditReservationService.reserve() freeze check order: client first, platform second, balance lock last — client-specific check is cheaper and fails faster for the common per-client case
- Cross-module service import of ClientFreezeService (account.service) into CreditReservationService (billing.service) documented in class Javadoc — service-to-service injection is permitted; prohibition is on repo-level cross-module imports
- SmsService.cancelScheduled() SUSPENDED support: additive statusAllowed boolean (ACCEPTED || SUSPENDED) preserves original semantics with self-documenting CFREEZE-04 comment

### Pending Todos

(None — clean slate for v1.2)

### Blockers/Concerns

- APIKEY_PEPPER env var must be set before application is used in production — fallback default is documented as unsafe
- Non-blocking tech debt from v1.0/v1.1:
  - Wrong error code for blank-message validation (INVALID_SENDER_ID used instead of message-specific code)
  - Duplicate @RateLimited on SmsResource AND SmsService fires AOP aspect twice per request (wastes rate-limit tokens)
  - TODO comments in AppEndpoints.java and SecurityConfiguration.java
  - WEBHOOK_DELETED enum constant unwired — needs trigger point when delete endpoint added
  - ClientCreditConsumptionResponse in analytics.contract (minor cross-module coupling with spend.service)
  - Boot smoke test for /v1/analytics/** endpoints recommended before production deploy

## Session Continuity

Last session: 2026-03-17
Stopped at: Phase 25, Plan 01 complete — three v1.3 audit tech debt items closed; 213 tests pass
Resume file: None
