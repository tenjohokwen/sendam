package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled job that periodically reconciles the Nexah-reported credit balance
 * against the Sendam-tracked platform balance (BALREC-01 through BALREC-04).
 *
 * <p>Design: NO class-level @Transactional — the Nexah HTTP call must NOT hold a DB
 * connection. The actual DB write is delegated to BalanceDeviationAlertService which
 * opens its own transaction only when a deviation is detected. This mirrors the
 * SegmentDeviationService split pattern from Phase 22.
 *
 * <p>Interval: configurable via {@code sendam.reconciliation.interval-minutes} (default 15 min).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BalanceReconciliationJob {

    private final NexahClient nexahClient;
    private final PlatformCreditService platformCreditService;
    private final BalanceDeviationAlertService alertService;

    /**
     * Reconciliation entry point. Runs at a fixed delay derived from
     * {@code sendam.reconciliation.interval-minutes} (default 15 minutes).
     *
     * <p>Execution flow:
     * <ol>
     *   <li>Fetch Nexah balance via HTTP — if unavailable, log warning and return (skip cycle).</li>
     *   <li>Fetch Sendam platform balance (short read-only transaction).</li>
     *   <li>Compute delta = nexahBalance - sendamBalance.</li>
     *   <li>If delta == 0: log DEBUG and return (no alert).</li>
     *   <li>If delta != 0: delegate to alertService.createAlert() to persist the deviation.</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "#{${sendam.reconciliation.interval-minutes:15} * 60000L}")
    public void reconcile() {
        long nexahBalance;
        try {
            nexahBalance = nexahClient.fetchCreditBalance();
        } catch (Exception e) {
            log.warn("Balance reconciliation skipped — Nexah unavailable: {}", e.getMessage());
            return;
        }

        long sendamBalance = platformCreditService.getBalance().balance();

        long delta = nexahBalance - sendamBalance;

        if (delta == 0) {
            log.debug("Balance reconciliation OK: nexah={}, sendam={}", nexahBalance, sendamBalance);
            return;
        }

        alertService.createAlert(nexahBalance, sendamBalance, delta);
    }
}
