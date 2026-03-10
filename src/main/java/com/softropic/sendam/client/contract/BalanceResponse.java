package com.softropic.sendam.client.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record BalanceResponse(
        @JsonProperty("available_balance") long availableBalance,
        String unit,
        String currency,
        @JsonProperty("last_updated_at") Instant lastUpdatedAt
) {}
