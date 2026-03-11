package com.softropic.sendam.gateway.analytics.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ClientCreditConsumptionResponse(
    @JsonProperty("net_credits_consumed") long    netCreditsConsumed,
    @JsonProperty("from")                 Instant from,
    @JsonProperty("to")                   Instant to
) {}
