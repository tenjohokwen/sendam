package com.softropic.sendam.gateway.webhook.contract;

import java.time.Instant;

public record WebhookEndpointRow(
    Long id,
    Long clientId,
    String publicId,
    String url,
    String events,
    String status,
    Instant createdDate
) {}
