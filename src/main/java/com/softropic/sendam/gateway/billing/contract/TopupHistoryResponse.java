package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TopupHistoryResponse(
    @JsonProperty("topups") List<TopupHistoryItem> topups
) {}
