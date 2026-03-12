package com.softropic.sendam.gateway.sms.contract;

import java.util.List;

public record ProviderDeliveryReportEvent(
    List<DlrEntry> dlrList
) {
    public record DlrEntry(
        String messageId,
        String status,
        String totalSmsUnit,
        String mobileNo
    ) {}
}
