package com.softropic.sendam.gateway.audit.repo;

import com.softropic.sendam.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_event", schema = "main")
@Getter
@Setter
public class AuditEventEntity extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
