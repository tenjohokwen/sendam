package com.softropic.sendam.gateway.billing.contract;

import java.util.List;

public record LedgerHistoryResponse(
        List<LedgerEntryDto> entries,
        int page,
        int size,
        long totalElements
) {}
