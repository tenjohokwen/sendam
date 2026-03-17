package com.softropic.sendam.gateway.billing.contract;

import jakarta.validation.constraints.Positive;

/**
 * Request to record a Nexah credit purchase into the platform ledger.
 *
 * @param amount    must be positive — credits being added to the platform balance
 * @param reference optional provider reference / order id (nullable)
 */
public record RecordNexahPurchaseRequest(
        @Positive(message = "amount must be positive") long amount,
        String reference
) {}
