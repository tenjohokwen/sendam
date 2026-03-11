package com.softropic.sendam.gateway.health.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookHealthResponse(
    @JsonProperty("total_attempts")  long totalAttempts,
    @JsonProperty("failure_count")   long failureCount,
    @JsonProperty("exhausted_count") long exhaustedCount
) {}
