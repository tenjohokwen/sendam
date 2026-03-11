---
phase: 09-spend-reporting-admin
verified: 2026-03-11T19:10:52Z
status: passed
score: 3/3 must-haves verified
re_verification: false
---

# Phase 9: Spend Reporting Admin Verification Report

**Phase Goal:** Admin can query credit consumption and top-up history per client
**Verified:** 2026-03-11T19:10:52Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Admin can query net credits consumed (debits minus refunds) per client for a given period | VERIFIED | `SpendRepository.findSpendSummary` executes native SQL with `COALESCE(SUM(CASE WHEN ... AS net_credits_consumed` formula covering SMS_DEBIT, SMS_RESERVATION, SMS_REFUND, TOPUP_APPROVED. All params nullable (`:clientId IS NULL OR ...`). `AdminSpendResource.getSpendSummary` wired to `SpendService.getSpendSummary` which calls repository and returns `SpendSummaryResponse.netCreditsConsumed`. |
| 2 | Response includes breakdown by ledger entry type (SMS_DEBIT, SMS_REFUND, TOPUP_APPROVED, SMS_RESERVATION) | VERIFIED | `SpendSummaryResponse` record has five `@JsonProperty` fields: `sms_debit`, `sms_refund`, `topup_approved`, `sms_reservation`, `net_credits_consumed`. SQL uses `ABS()` for negative-stored types (SMS_DEBIT, SMS_RESERVATION). TOPUP_PENDING is absent from the SQL — correctly excluded. All SQL aliases match `SpendSummaryRow` getter camelCase names exactly. |
| 3 | Admin can view top-up history (pending / approved / rejected) per client per period | VERIFIED | `SpendRepository.findTopupHistory` queries `main.topup_request` with optional `clientId`, `topupStatus` (String, passed through directly — avoids Hibernate enum-binding), `from`, `to` params. `AdminSpendResource.getTopupHistory` maps `topupStatus` as `required = false`. `SpendService` maps 10 `TopupHistoryRow` fields to `TopupHistoryItem` records with null guards for nullable `approved_at`/`rejected_at` timestamps. Response wraps list in `TopupHistoryResponse.topups`. |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V9__spend_index.sql` | Composite index on topup_request(client_id, created_date DESC) | VERIFIED | 2 lines; exact SQL: `CREATE INDEX idx_topup_client_date ON main.topup_request(client_id, created_date DESC)` |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryRow.java` | Projection interface with 5 getters | VERIFIED | 9 lines; 5 getters: `getSmsDebit`, `getSmsRefund`, `getTopupApproved`, `getSmsReservation`, `getNetCreditsConsumed` — all match SQL aliases exactly |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryRow.java` | Projection interface with 10 getters including nullable Timestamps | VERIFIED | 16 lines; 10 getters with `java.sql.Timestamp` for `createdDate`, `approvedAt`, `rejectedAt` |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/SpendSummaryResponse.java` | Response record with 5 @JsonProperty fields | VERIFIED | 11 lines; snake_case JSON names via `@JsonProperty` |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryItem.java` | Single topup DTO record with 10 fields and nullable Instants | VERIFIED | 17 lines; 10 `@JsonProperty` fields; `approvedAt`/`rejectedAt` as nullable `Instant` |
| `src/main/java/com/softropic/sendam/gateway/spend/contract/TopupHistoryResponse.java` | Wrapper record with topups list | VERIFIED | 8 lines; wraps `List<TopupHistoryItem>` as `topups` |
| `src/main/java/com/softropic/sendam/gateway/spend/repo/SpendRepository.java` | `Repository<Object, Long>` with two native @Query methods | VERIFIED | 66 lines; extends `Repository<Object, Long>`; `findSpendSummary` and `findTopupHistory` both with `nativeQuery = true` |
| `src/main/java/com/softropic/sendam/gateway/spend/service/SpendService.java` | @Transactional(readOnly=true) service mapping projections to response records | VERIFIED | 52 lines; `@Service @RequiredArgsConstructor @Transactional(readOnly = true)`; both methods fully implemented with null guards |
| `src/main/java/com/softropic/sendam/gateway/spend/api/AdminSpendResource.java` | Admin REST controller at /api/admin/spend | VERIFIED | 43 lines; `@RestController @RequestMapping("/api/admin/spend")`; two `@GetMapping` methods each with `@PreAuthorize("hasRole('ADMIN')")` |
| `src/main/java/com/softropic/sendam/security/config/AppEndpoints.java` | ADMIN_SPEND constant + SECURED_MAPPINGS entry with ADMIN authority | VERIFIED | Line 29: `ADMIN_SPEND = "/api/admin/spend/**"`; line 52: `ADMIN_SPEND, new String[]{AuthoritiesConstants.ADMIN}` in `Map.of()` (9 of 10 allowed pairs used) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AdminSpendResource` | `SpendService` | `@RequiredArgsConstructor` field injection | WIRED | `private final SpendService spendService` declared and called on both handler methods |
| `SpendService` | `SpendRepository` | `@RequiredArgsConstructor` field injection | WIRED | `private final SpendRepository repository` declared; both `findSpendSummary` and `findTopupHistory` called |
| `SpendRepository` | `main.credit_ledger_entry` | native `@Query` | WIRED | `FROM main.credit_ledger_entry e` on line 30 |
| `SpendRepository` | `main.topup_request` | native `@Query` | WIRED | `FROM main.topup_request t` on line 53 |
| `AppEndpoints.SECURED_MAPPINGS` | `ADMIN_SPEND` path | `Map.of()` static initializer | WIRED | `ADMIN_SPEND, new String[]{AuthoritiesConstants.ADMIN}` at line 52 |
| `SpendService.getTopupHistory` | nullable Timestamp conversion | null guard before `.toInstant()` | WIRED | Lines 46-47: `r.getApprovedAt() != null ? r.getApprovedAt().toInstant() : null` |

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| SPEN-01: `GET /api/admin/spend/credits` returns `net_credits_consumed` | SATISFIED | `AdminSpendResource.getSpendSummary` at `@GetMapping("/credits")`; `SpendSummaryResponse.netCreditsConsumed` serialized as `net_credits_consumed` |
| SPEN-02: Breakdown by ledger entry type with positive absolute values; TOPUP_PENDING omitted | SATISFIED | SQL uses `ABS()` for SMS_DEBIT and SMS_RESERVATION; TOPUP_PENDING absent from all `CASE WHEN` branches; 4 breakdown fields in response |
| SPEN-03: `GET /api/admin/spend/topups` with optional `topupStatus` filter | SATISFIED | `@GetMapping("/topups")`; `topupStatus` is `@RequestParam(required = false) String`; passed directly to repository; all topup_status values (PENDING_APPROVAL, APPROVED, REJECTED) included when omitted |
| Security: ADMIN JWT required; 401 unauthenticated; 403 non-admin | SATISFIED (structural) | `AppEndpoints.SECURED_MAPPINGS` enforces path-level ADMIN authority; `@PreAuthorize("hasRole('ADMIN')")` on each method provides method-level enforcement |

### Anti-Patterns Found

None. No TODO, FIXME, placeholder, stub patterns, or empty returns found across the `gateway/spend` module.

### Human Verification Required

The following items require a running application to confirm runtime behaviour. Automated structural checks all pass.

#### 1. SQL alias-to-projection-getter binding at runtime

**Test:** Call `GET /api/admin/spend/credits` with an admin JWT against a database that has credit_ledger_entry rows of each type. Inspect the JSON response.
**Expected:** `sms_debit`, `sms_refund`, `topup_approved`, `sms_reservation`, `net_credits_consumed` are all non-zero and reflect actual DB values.
**Why human:** Spring Data's camelCase-to-underscore alias binding is correct structurally, but silent mismatches return 0 with no exception. Only a live query confirms the wire is live.

#### 2. `net_credits_consumed` sign semantics

**Test:** With known data (e.g. 100 SMS_DEBIT at -500 each, 10 SMS_REFUND at +200 each), verify `net_credits_consumed` in the response equals `(100 * 500) - (10 * 200) = 48000`.
**Expected:** Positive integer representing credits consumed (debits minus refunds).
**Why human:** The formula `ABS(SMS_DEBIT+SMS_RESERVATION) - SMS_REFUND - TOPUP_APPROVED` is correct conceptually but runtime arithmetic on real data is required to confirm the sign convention matches the intent.

#### 3. 401/403 security enforcement at runtime

**Test:** (a) Call either endpoint with no Authorization header. (b) Call either endpoint with a valid client (non-admin) JWT.
**Expected:** (a) HTTP 401. (b) HTTP 403.
**Why human:** Security filter chain order and `@PreAuthorize` interaction requires a running Spring Security context to confirm.

#### 4. Optional `topupStatus` filter returns all statuses when omitted

**Test:** Call `GET /api/admin/spend/topups` with no `topupStatus` param against a database with rows in all three statuses.
**Expected:** Response includes topups of all statuses (PENDING_APPROVAL, APPROVED, REJECTED).
**Why human:** The `:topupStatus IS NULL OR ...` SQL pattern is correct but Hibernate's handling of null String params in native queries must be confirmed at runtime.

---

## Summary

All 10 required artifacts exist with substantive implementations. All 6 key links are verified wired. The spend module is self-contained under `gateway/spend` with correct layering (contract, repo, service, api). No stubs or anti-patterns detected. Security is registered at both the filter-chain level (AppEndpoints) and the method level (@PreAuthorize). Four items are flagged for human verification at runtime — none are blockers to code review; they are runtime behaviour confirmations that cannot be asserted by static analysis.

---

_Verified: 2026-03-11T19:10:52Z_
_Verifier: Claude (gsd-verifier)_
