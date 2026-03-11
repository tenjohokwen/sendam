package com.softropic.sendam.gateway.analytics.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientDeliveryStatsResponse(
    @JsonProperty("total_sent")     long   totalSent,
    @JsonProperty("delivered")      long   delivered,
    @JsonProperty("failed")         long   failed,
    @JsonProperty("delivery_rate")  double deliveryRate,
    @JsonProperty("total_segments") long   totalSegments
) {}
