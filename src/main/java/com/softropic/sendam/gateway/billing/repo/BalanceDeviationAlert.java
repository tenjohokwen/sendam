package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA entity for balance deviation alerts.
 *
 * Persisted when BalanceReconciliationJob detects a mismatch between
 * the Nexah-reported credit balance and the Sendam-tracked platform balance.
 * No FK to send_request — this is a standalone periodic audit record.
 */
@Slf4j
@Entity
@Table(name = "balance_deviation_alert", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BalanceDeviationAlert extends AbstractAuditingEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private DeviationAlertType alertType;

    @Column(name = "nexah_balance", nullable = false)
    private long nexahBalance;

    @Column(name = "sendam_balance", nullable = false)
    private long sendamBalance;

    @Column(name = "delta", nullable = false)
    private long delta;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
