package com.softropic.sendam.gateway.sms.contract;

public record DlrRow(
    Long id,
    String recipient,
    String sendStatus,
    String gatewayMessageId,
    String providerMessageId,
    Integer segmentsConsumed
) {}
