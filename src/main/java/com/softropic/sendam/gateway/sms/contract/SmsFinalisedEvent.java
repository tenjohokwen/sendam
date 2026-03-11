package com.softropic.sendam.gateway.sms.contract;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;

import java.util.List;

/**
 * Spring ApplicationEvent (POJO style — no extends needed since Spring 4.2).
 * Published by DrCallbackService after a SendRequest reaches FINALIZED or FAIL_FINALIZED.
 * Consumed by SmsFinalisedListener (infrastructure/listener) via @TransactionalEventListener.
 *
 * <p>Carries per-recipient summaries so the listener can fan out into one WebhookDelivery
 * row per recipient (matching the per-recipient shape of the sms.finalized event in the v8 contract).
 */
public record SmsFinalisedEvent(
        Long clientId,
        String sendRequestId,
        List<RecipientSummary> recipients
) {
    public record RecipientSummary(
            String recipient,
            String gatewayMessageId,
            SendRequestStatus finalStatus
    ) {}
}
