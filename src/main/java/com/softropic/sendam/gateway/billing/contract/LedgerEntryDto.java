package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record LedgerEntryDto(
        Instant timestamp,
        LedgerEntryType type,
        long amount,
        @JsonProperty("balance_after") long balanceAfter,
        String reference
) {}
