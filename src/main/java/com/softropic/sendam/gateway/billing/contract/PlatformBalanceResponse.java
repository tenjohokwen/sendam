package com.softropic.sendam.gateway.billing.contract;

import java.time.Instant;

/**
 * Current platform credit balance snapshot.
 *
 * @param balance  current balance in SMS segments
 * @param asOf     lastModifiedDate of the singleton balance row
 */
public record PlatformBalanceResponse(
        long balance,
        Instant asOf
) {}
