package com.softropic.sendam.gateway.sms.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageStatusEntry(
    String recipient,
    String state,
    @JsonProperty("gateway_message_id") String gatewayMessageId,
    @JsonProperty("provider_message_id") String providerId,
    @JsonProperty("segments_consumed") Integer segmentsConsumed
) {}
