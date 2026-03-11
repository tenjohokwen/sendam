package com.softropic.sendam.gateway.webhook.repo;

import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

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

import java.time.Instant;

/**
 * JPA entity tracking each outbound webhook delivery attempt.
 * One row is created per recipient per finalized SendRequest.
 *
 * <p>{@code deliveryStatus} holds the semantic SMS outcome ("DELIVERED" or "FAILED")
 * that will be included in the webhook payload.
 * {@code attemptStatus} is the webhook delivery lifecycle state
 * (PENDING → DELIVERED | FAILED → EXHAUSTED).
 * The inherited {@code status} column (EntityStatus) remains ACTIVE for entity lifecycle.
 */
@Entity
@Table(name = "webhook_delivery", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WebhookDelivery extends AbstractAuditingEntity {

    @Column(name = "webhook_endpoint_id", nullable = false)
    private Long webhookEndpointId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "send_request_id", nullable = false, length = 200)
    private String sendRequestId;

    @Column(name = "recipient", nullable = false, length = 20)
    private String recipient;

    @Column(name = "gateway_message_id", length = 100)
    private String gatewayMessageId;

    /** Semantic SMS delivery outcome: "DELIVERED" or "FAILED". Stored in webhook payload. */
    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Webhook delivery attempt lifecycle state. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    private WebhookDeliveryStatus attemptStatus = WebhookDeliveryStatus.PENDING;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
