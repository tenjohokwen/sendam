package com.softropic.sendam.gateway.provider.nexah.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CircuitBreakerHealthResponse(
    @JsonProperty("state")               String state,
    @JsonProperty("failure_rate_pct")    float  failureRatePct,
    @JsonProperty("failed_calls")        int    failedCalls,
    @JsonProperty("successful_calls")    int    successfulCalls,
    @JsonProperty("not_permitted_calls") long   notPermittedCalls
) {}
