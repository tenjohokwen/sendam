package com.softropic.sendam.gateway.sms.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendSmsResponse(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("sendRequestId") String sendRequestId,
    @JsonProperty("message_count") int messageCount,
    @JsonProperty("calculated_segment_count") int calculatedSegmentCount,
    @JsonProperty("reserved_credits") long reservedCredits,
    @JsonProperty("available_balance_after_reservation") long availableBalanceAfterReservation,
    String status
) {}
