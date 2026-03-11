package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /v1/credits/topups.
 * amount is in SMS segments (whole units, must be positive).
 */
public record CreateTopupRequest(
        @NotNull @Positive long amount,
        @NotBlank @JsonProperty("transaction_id") String transactionId,
        @NotBlank @JsonProperty("payment_type") String paymentType,
        @JsonProperty("account_number") String accountNumber
) {
}
