package com.softropic.sendam.gateway.account.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "client_account", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity extends AbstractAuditingEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;

    @Column(name = "frozen", nullable = false)
    @Builder.Default
    private boolean frozen = false;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "freeze_reason", columnDefinition = "TEXT")
    private String freezeReason;

    @Column(name = "freeze_resolved_at")
    private Instant freezeResolvedAt;

    @Column(name = "freeze_resolution", columnDefinition = "TEXT")
    private String freezeResolution;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(final boolean frozen) {
        this.frozen = frozen;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(final Instant frozenAt) {
        this.frozenAt = frozenAt;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(final String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public Instant getFreezeResolvedAt() {
        return freezeResolvedAt;
    }

    public void setFreezeResolvedAt(final Instant freezeResolvedAt) {
        this.freezeResolvedAt = freezeResolvedAt;
    }

    public String getFreezeResolution() {
        return freezeResolution;
    }

    public void setFreezeResolution(final String freezeResolution) {
        this.freezeResolution = freezeResolution;
    }
}
