---
phase: 23-periodic-balance-reconciliation
plan: 01
subsystem: billing
tags: [flyway, jpa, spring-boot, configuration-properties, nexah, reconciliation]

# Dependency graph
requires:
  - phase: 22-final-booking-segment-deviation
    provides: SegmentDeviationAlert entity + DeviationAlertType + billing.repo conventions
  - phase: 19-platform-credit-tracking
    provides: AbstractAuditingEntity, EntityStatus, PlatformCreditBalance patterns

provides:
  - V15 Flyway migration — balance_deviation_alert table with BIGINT PK
  - BalanceDeviationAlert JPA entity (billing.repo)
  - BalanceDeviationAlertRepository stub (billing.repo)
  - ReconciliationProperties @ConfigurationProperties record (billing.contract)
  - BillingConfig @Configuration class enabling ReconciliationProperties binding
  - DeviationAlertType.BALANCE constant
  - AuditEventType.BALANCE_DEVIATION constant
  - NexahClient.fetchCreditBalance() method

affects:
  - 23-02 (BalanceReconciliationJob directly depends on all artifacts in this plan)
  - 24 (Phase 24 admin UI queries BalanceDeviationAlertRepository)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - ReconciliationProperties binds via @EnableConfigurationProperties in BillingConfig (billing.config package established)
    - fetchCreditBalance() is NOT circuit-breaker-wrapped — background probe pattern (same as checkAvailability())

key-files:
  created:
    - src/main/resources/db/migration/V15__balance_deviation_alert.sql
    - src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlert.java
    - src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlertRepository.java
    - src/main/java/com/softropic/sendam/gateway/billing/contract/ReconciliationProperties.java
    - src/main/java/com/softropic/sendam/gateway/billing/config/BillingConfig.java
  modified:
    - src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertType.java
    - src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java
    - src/main/java/com/softropic/sendam/gateway/provider/nexah/infrastructure/NexahClient.java
    - src/main/resources/application.yaml

key-decisions:
  - "BillingConfig does NOT add @EnableScheduling — ClientConfig (sms.config) already has it; duplicate adds confusion"
  - "fetchCreditBalance() reads top-level credit field, not balance[].credit array — top-level is total across all countries"
  - "BalanceDeviationAlertRepository is a minimal stub — Phase 24 adds query methods for listing/filtering"
  - "BillingConfig placed in billing.config package — establishes new config subpackage for billing module"

patterns-established:
  - "billing.config package: billing module configuration lives here (mirrors sms.config, account.config patterns)"
  - "Non-circuit-breaker Nexah calls: background jobs call fetchCreditBalance() directly without CB wrapping"

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 23 Plan 01: Periodic Balance Reconciliation Foundation Summary

**balance_deviation_alert Flyway table, BalanceDeviationAlert JPA entity + repository stub, ReconciliationProperties config, BillingConfig, and NexahClient.fetchCreditBalance() — full persistence/config foundation for Plan 02's reconciliation job**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-17T15:41:48Z
- **Completed:** 2026-03-17T15:46:21Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- V15 Flyway migration creates `main.balance_deviation_alert` with BIGINT PK, status/created_date indexes
- BalanceDeviationAlert entity maps all columns; no FK to send_request — standalone audit table
- ReconciliationProperties + BillingConfig wired so Spring binds `sendam.reconciliation.interval-minutes`
- NexahClient.fetchCreditBalance() fetches top-level credit field from /smscredit; throws ProviderUnavailableException on null/non-1 responsecode
- 203 tests pass, zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: V15 Flyway migration + enum additions** - `4507f76` (feat)
2. **Task 2: BalanceDeviationAlert entity + repository + config infrastructure** - `d03dccc` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V15__balance_deviation_alert.sql` - Balance deviation alert table DDL
- `src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlert.java` - JPA entity (nexahBalance, sendamBalance, delta fields)
- `src/main/java/com/softropic/sendam/gateway/billing/repo/BalanceDeviationAlertRepository.java` - Minimal stub, Phase 24 adds queries
- `src/main/java/com/softropic/sendam/gateway/billing/contract/ReconciliationProperties.java` - @ConfigurationProperties(prefix = "sendam.reconciliation")
- `src/main/java/com/softropic/sendam/gateway/billing/config/BillingConfig.java` - @Configuration @EnableConfigurationProperties(ReconciliationProperties.class)
- `src/main/java/com/softropic/sendam/gateway/billing/contract/DeviationAlertType.java` - Added BALANCE constant
- `src/main/java/com/softropic/sendam/gateway/audit/contract/AuditEventType.java` - Added BALANCE_DEVIATION constant (BALREC-03)
- `src/main/java/com/softropic/sendam/gateway/provider/nexah/infrastructure/NexahClient.java` - Added fetchCreditBalance() method
- `src/main/resources/application.yaml` - Added sendam.reconciliation.interval-minutes: 15

## Decisions Made

- **BillingConfig has no @EnableScheduling** — ClientConfig (sms.config package) already enables scheduling; adding it here adds confusion without benefit.
- **fetchCreditBalance() reads top-level credit, not balance[].credit** — the balance array is per-country; top-level credit is the total across all countries. Consistent with existing checkAvailability() logic.
- **BalanceDeviationAlertRepository is a minimal stub** — only save() needed by Plan 02's job; query methods for admin listing deferred to Phase 24.
- **billing.config package created** — establishes new config subpackage for billing module, mirroring sms.config and account.config conventions.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

All Plan 02 dependencies are in place:
- `DeviationAlertType.BALANCE` available
- `AuditEventType.BALANCE_DEVIATION` available
- `BalanceDeviationAlert` + `BalanceDeviationAlertRepository` in billing.repo
- `ReconciliationProperties` injectable via `BillingConfig`
- `NexahClient.fetchCreditBalance()` callable from job
- 203 tests passing, no blockers

---
*Phase: 23-periodic-balance-reconciliation*
*Completed: 2026-03-17*
