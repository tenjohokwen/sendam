package com.softropic.sendam.gateway.billing.contract;

import java.time.Instant;

/**
 * Response returned after successfully recording a Nexah purchase in the platform ledger.
 *
 * @param newBalance      platform balance after the purchase was applied
 * @param amountRecorded  the amount that was added (mirrors the request amount)
 * @param recordedAt      wall-clock time when the entry was written
 */
public record RecordNexahPurchaseResponse(
        long newBalance,
        long amountRecorded,
        Instant recordedAt
) {}
