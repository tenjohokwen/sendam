package com.softropic.sendam.gateway.sms.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DeliveryStatsResponse(
    @JsonProperty("total_sent")       long                    totalSent,
    @JsonProperty("delivered")        long                    delivered,
    @JsonProperty("failed")           long                    failed,
    @JsonProperty("delivery_rate")    double                  deliveryRate,
    @JsonProperty("total_segments")   long                    totalSegments,
    @JsonProperty("daily_breakdown")  List<DeliveryDailyStat> dailyBreakdown
) {}
