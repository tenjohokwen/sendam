package com.softropic.sendam.client.contract;

import java.util.List;

public record LedgerHistoryResponse(
        List<LedgerEntryDto> entries,
        int page,
        int size,
        long totalElements
) {}
