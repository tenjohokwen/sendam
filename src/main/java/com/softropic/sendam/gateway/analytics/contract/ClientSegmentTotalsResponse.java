package com.softropic.sendam.gateway.analytics.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ClientSegmentTotalsResponse(
    @JsonProperty("total_segments") long    totalSegments,
    @JsonProperty("from")           Instant from,
    @JsonProperty("to")             Instant to
) {}
