package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "platform_freeze_state", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlatformFreezeState extends AbstractAuditingEntity {

    @Column(name = "frozen", nullable = false)
    @Builder.Default
    private boolean frozen = false;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "freeze_reason", columnDefinition = "TEXT")
    private String freezeReason;

    /**
     * Shortfall amount that triggered an automatic platform freeze (Phase 22 PFLAT-03).
     * Null for manually-initiated admin freezes.
     */
    @Column(name = "shortfall_amount")
    private Long shortfallAmount;

    @Column(name = "freeze_resolved_at")
    private Instant freezeResolvedAt;

    @Column(name = "freeze_resolution", columnDefinition = "TEXT")
    private String freezeResolution;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
