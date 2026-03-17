package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Single platform ledger entry for API responses.
 * Mirrors LedgerEntryDto but for the platform account.
 */
public record PlatformLedgerEntryDto(
        Instant timestamp,
        PlatformLedgerEntryType entryType,
        long amount,
        @JsonProperty("balance_after") long balanceAfter,
        String reference
) {}
