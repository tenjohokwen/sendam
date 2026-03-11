package com.softropic.sendam.gateway.analytics.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeliveryDailyStat(
    @JsonProperty("date")        String date,
    @JsonProperty("total_sent")  long   totalSent,
    @JsonProperty("delivered")   long   delivered,
    @JsonProperty("failed")      long   failed
) {}
