package com.softropic.sendam.gateway.sms.contract;

import java.util.List;

/**
 * Event published when an SMS SendRequest has reached a terminal state
 * (either FINALIZED or FAIL_FINALIZED).
 */
public record SmsFinalisedEvent(
    Long clientId,
    String sendRequestId,
    List<RecipientSummary> recipients,
    Long reservationId,
    Long actualSegments
) {
    public record RecipientSummary(
        String recipient,
        String gatewayMessageId,
        SendRequestStatus finalStatus
    ) {}
}
