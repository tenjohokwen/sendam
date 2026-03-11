package com.softropic.sendam.gateway.sms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.webhook.repo.WebhookDelivery;
import com.softropic.sendam.gateway.webhook.repo.WebhookDeliveryRepository;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpoint;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpointRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens for {@link SmsFinalisedEvent} published after a SendRequest reaches
 * FINALIZED or FAIL_FINALIZED, then writes one {@link WebhookDelivery} row per
 * recipient per active webhook endpoint registered to that client.
 *
 * <p><strong>Transaction design:</strong> {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * runs after the outer finalization transaction commits, so delivery rows are never created
 * for rolled-back finalizations. {@code @Transactional(propagation = REQUIRES_NEW)} opens a
 * fresh transaction to write the delivery rows — without it there is no ambient transaction
 * and {@code webhookDeliveryRepository.save()} would throw {@code TransactionRequiredException}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsFinalisedListener {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates PENDING WebhookDelivery rows for every active endpoint belonging to the
     * finalized client.  One row is created per recipient per endpoint.
     *
     * <p>Runs in its own REQUIRES_NEW transaction after the outer TX commits.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSmsFinalized(SmsFinalisedEvent event) {
        List<WebhookEndpoint> endpoints =
                webhookEndpointRepository.findByClientIdAndStatus(event.clientId(), EntityStatus.ACTIVE);

        if (endpoints.isEmpty()) {
            log.debug("No active webhook endpoints for clientId={} — skipping delivery creation",
                    event.clientId());
            return;
        }

        for (WebhookEndpoint endpoint : endpoints) {
            for (SmsFinalisedEvent.RecipientSummary summary : event.recipients()) {
                String semanticStatus = toSemanticStatus(summary.finalStatus());
                String payload = buildPayload(event.sendRequestId(), summary, semanticStatus);

                WebhookDelivery delivery = new WebhookDelivery();
                delivery.setWebhookEndpointId(endpoint.getId());
                delivery.setClientId(event.clientId());
                delivery.setSendRequestId(event.sendRequestId());
                delivery.setRecipient(summary.recipient());
                delivery.setGatewayMessageId(summary.gatewayMessageId());
                delivery.setDeliveryStatus(semanticStatus);
                delivery.setPayload(payload);
                delivery.setAttemptStatus(WebhookDeliveryStatus.PENDING);
                delivery.setAttemptCount(0);
                delivery.setNextAttemptAt(Instant.now());

                webhookDeliveryRepository.save(delivery);
            }
        }

        log.debug("Created {} webhook delivery row(s) for sendRequestId={} clientId={}",
                endpoints.size() * event.recipients().size(), event.sendRequestId(), event.clientId());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the internal finalization status to the semantic status string used in the
     * v8 contract webhook payload (section 12.2).
     */
    private static String toSemanticStatus(SendRequestStatus finalStatus) {
        return finalStatus == SendRequestStatus.FINALIZED ? "DELIVERED" : "FAILED";
    }

    /**
     * Builds the JSON payload matching the v8 contract section 12.2:
     * <pre>
     * {"event":"sms.finalized","sendRequestId":"...","recipient":"...","gateway_message_id":"...","status":"DELIVERED"}
     * </pre>
     *
     * <p>Falls back to a manually constructed string if Jackson serialization fails, so a
     * serialization error never silently drops delivery rows.
     */
    private String buildPayload(String sendRequestId,
                                SmsFinalisedEvent.RecipientSummary summary,
                                String semanticStatus) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("event", "sms.finalized");
            map.put("sendRequestId", sendRequestId);
            map.put("recipient", summary.recipient());
            map.put("gateway_message_id", summary.gatewayMessageId() != null ? summary.gatewayMessageId() : "");
            map.put("status", semanticStatus);
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook payload for sendRequestId={} recipient={} — using fallback",
                    sendRequestId, summary.recipient(), e);
            return "{\"event\":\"sms.finalized\",\"sendRequestId\":\"" + sendRequestId
                    + "\",\"recipient\":\"" + summary.recipient()
                    + "\",\"status\":\"" + semanticStatus + "\"}";
        }
    }
}
