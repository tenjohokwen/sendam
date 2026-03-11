package com.softropic.sendam.gateway.spend.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpendSummaryResponse(
    @JsonProperty("sms_debit")             long smsDebit,
    @JsonProperty("sms_refund")            long smsRefund,
    @JsonProperty("topup_approved")        long topupApproved,
    @JsonProperty("sms_reservation")       long smsReservation,
    @JsonProperty("net_credits_consumed")  long netCreditsConsumed
) {}
