package com.softropic.sendam.gateway.analytics.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record SegmentTotalsResponse(
    @JsonProperty("total_segments")  long    totalSegments,
    @JsonProperty("client_id")       Long    clientId,
    @JsonProperty("from")            Instant from,
    @JsonProperty("to")              Instant to
) {}
