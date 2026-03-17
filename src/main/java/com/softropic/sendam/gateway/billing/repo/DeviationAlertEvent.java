package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.common.persistence.BaseEntity;
import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Audit trail record for each deviation alert status transition.
 *
 * Extends BaseEntity (not AbstractAuditingEntity) — owns its own acted_at/acted_by
 * fields instead of the Spring Security-derived created_by/last_modified_by columns.
 *
 * Exactly one of segmentAlertIdFk or balanceAlertIdFk is non-null per row:
 *   - SEGMENT / PLATFORM_FREEZE alerts → segmentAlertIdFk set, balanceAlertIdFk null
 *   - BALANCE alerts                   → balanceAlertIdFk set, segmentAlertIdFk null
 */
@Slf4j
@Entity
@Table(name = "deviation_alert_event", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeviationAlertEvent extends BaseEntity {

    @Column(name = "segment_alert_id_fk")
    private Long segmentAlertIdFk;  // null for BALANCE rows

    @Column(name = "balance_alert_id_fk")
    private Long balanceAlertIdFk;  // null for SEGMENT/PLATFORM_FREEZE rows

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private DeviationAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private AlertStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AlertStatus newStatus;

    @Column(name = "admin_note", nullable = false, columnDefinition = "text")
    private String adminNote;

    @Column(name = "acted_by", nullable = false, length = 50)
    private String actedBy;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;
}
