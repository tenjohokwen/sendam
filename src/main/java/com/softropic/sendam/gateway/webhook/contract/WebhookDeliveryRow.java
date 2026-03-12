package com.softropic.sendam.gateway.webhook.contract;

import java.time.Instant;

public record WebhookDeliveryRow(
    Long id,
    Long clientId,
    String sendRequestId,
    String recipient,
    String deliveryStatus,
    String attemptStatus,
    int attemptCount,
    Instant lastAttemptAt,
    Instant nextAttemptAt,
    Integer httpStatus
) {}
