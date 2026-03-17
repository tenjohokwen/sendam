package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin @Transactional write service for balance deviation alerts.
 *
 * Mirrors SegmentDeviationService: owns the DB write and audit event publication,
 * but does NOT perform the HTTP call or comparison — that belongs to BalanceReconciliationJob.
 * Keeping the write in a separate service ensures the DB connection is NOT held during
 * the Nexah HTTP call (BALREC-03).
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BalanceDeviationAlertService {

    private final BalanceDeviationAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Persists a BALANCE deviation alert and publishes an audit event.
     * Called by BalanceReconciliationJob only when delta != 0.
     *
     * @param nexahBalance  credits reported by Nexah /smscredit
     * @param sendamBalance credits tracked by the Sendam platform balance
     * @param delta         nexahBalance - sendamBalance (non-zero)
     */
    public void createAlert(long nexahBalance, long sendamBalance, long delta) {
        BalanceDeviationAlert alert = BalanceDeviationAlert.builder()
                .alertType(DeviationAlertType.BALANCE)
                .nexahBalance(nexahBalance)
                .sendamBalance(sendamBalance)
                .delta(delta)
                .status(EntityStatus.ACTIVE)
                .build();
        alertRepository.save(alert);

        // clientId is null — platform-level event has no client target
        eventPublisher.publishEvent(new DomainAuditEvent(
                AuditEventType.BALANCE_DEVIATION,
                null,
                "system",
                "Balance deviation detected: nexah=" + nexahBalance
                        + ", sendam=" + sendamBalance
                        + ", delta=" + delta
        ));

        log.warn("Balance deviation alert created: nexahBalance={}, sendamBalance={}, delta={}",
                nexahBalance, sendamBalance, delta);
    }
}
