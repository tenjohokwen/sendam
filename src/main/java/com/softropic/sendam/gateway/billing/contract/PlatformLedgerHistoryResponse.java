package com.softropic.sendam.gateway.billing.contract;

import java.util.List;

/**
 * Paginated platform ledger history response.
 * Mirrors LedgerHistoryResponse but for the platform account.
 */
public record PlatformLedgerHistoryResponse(
        List<PlatformLedgerEntryDto> entries,
        int page,
        int size,
        long totalElements
) {}
