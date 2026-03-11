package com.softropic.sendam.gateway.health.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProviderStatsResponse(
    @JsonProperty("total_submitted")  long   totalSubmitted,
    @JsonProperty("dr_received")      long   drReceived,
    @JsonProperty("failed_count")     long   failedCount,
    @JsonProperty("failure_rate_pct") double failureRatePct
) {}
