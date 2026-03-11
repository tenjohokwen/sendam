package com.softropic.sendam.gateway.sms.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancelSmsResponse(
    @JsonProperty("sendRequestId") String sendRequestId,
    String status
) {}
