package com.softropic.sendam.client.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MessageStatusResponse(
    @JsonProperty("sendRequestId") String sendRequestId,
    @JsonProperty("overall_status") String overallStatus,
    int page,
    @JsonProperty("page_size") int pageSize,
    @JsonProperty("total_messages") long totalMessages,
    List<MessageStatusEntry> messages
) {}
