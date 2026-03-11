package com.softropic.sendam.client.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record RegisterWebhookResponse(
        @JsonProperty("webhook_id") String webhookId,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {}
