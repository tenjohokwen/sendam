package com.softropic.sendam.gateway.webhook.repo;

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

/**
 * JPA entity for client webhook endpoint registrations.
 * One row per client (enforced by UNIQUE(client_id) constraint).
 * The inherited {@code status} column (EntityStatus) reflects the entity lifecycle.
 * {@code events} stores a comma-separated list of subscribed event types (e.g. "sms.finalized").
 */
@Entity
@Table(name = "webhook_endpoint", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WebhookEndpoint extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /**
     * Public-facing identifier returned to clients (e.g. "wh_a1b2c3d4e5f6").
     * Never exposes the internal BIGINT PK.
     */
    @Column(name = "public_id", nullable = false, length = 30, unique = true)
    private String publicId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    /** Comma-separated event types this endpoint subscribes to. */
    @Column(name = "events", nullable = false, length = 100)
    private String events;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
