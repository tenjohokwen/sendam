package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.RegisterWebhookRequest;
import com.softropic.sendam.client.contract.RegisterWebhookResponse;
import com.softropic.sendam.client.repo.WebhookEndpoint;
import com.softropic.sendam.client.repo.WebhookEndpointRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for webhook registration and (in Plan 05-02) delivery dispatch.
 *
 * <p>{@link #register(Long, RegisterWebhookRequest)} upserts a webhook endpoint:
 * if the client already has a registered endpoint the URL is updated and the existing
 * {@code public_id} is returned; otherwise a new endpoint is created.
 *
 * <p>{@code applicationEventPublisher} is declared here now so the constructor is
 * complete for Plan 05-02, which will add the scheduled delivery poller and the
 * per-attempt dispatch logic that publishes no events itself (events flow in from
 * {@code DrCallbackService} via {@code SmsFinalisedEvent}).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Registers or updates a client webhook endpoint (upsert semantics).
     *
     * <ul>
     *   <li>If the client already has an endpoint: updates URL, resets status to ACTIVE, returns
     *       the existing {@code public_id} and original {@code created_at}.</li>
     *   <li>If no endpoint exists: creates a new one with a generated {@code wh_xxx} public id.</li>
     * </ul>
     *
     * @param clientId the authenticated client id
     * @param request  the registration payload (url + optional event list)
     * @return response with webhook_id, status, and created_at
     */
    public RegisterWebhookResponse register(Long clientId, RegisterWebhookRequest request) {
        return webhookEndpointRepository.findByClientId(clientId)
                .map(existing -> updateExisting(existing, request))
                .orElseGet(() -> createNew(clientId, request));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RegisterWebhookResponse updateExisting(WebhookEndpoint existing,
                                                   RegisterWebhookRequest request) {
        existing.setUrl(request.url());
        existing.setStatus(EntityStatus.ACTIVE);
        WebhookEndpoint saved = webhookEndpointRepository.save(existing);
        log.debug("Updated webhook endpoint id={} publicId={}", saved.getId(), saved.getPublicId());
        return new RegisterWebhookResponse(
                saved.getPublicId(),
                EntityStatus.ACTIVE.name(),
                saved.getCreatedDate()
        );
    }

    private RegisterWebhookResponse createNew(Long clientId, RegisterWebhookRequest request) {
        String publicId = generatePublicId();
        String events = resolveEvents(request.events());

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setClientId(clientId);
        endpoint.setPublicId(publicId);
        endpoint.setUrl(request.url());
        endpoint.setEvents(events);
        endpoint.setStatus(EntityStatus.ACTIVE);

        WebhookEndpoint saved = webhookEndpointRepository.save(endpoint);
        log.debug("Created webhook endpoint id={} publicId={} for clientId={}",
                saved.getId(), saved.getPublicId(), clientId);
        return new RegisterWebhookResponse(
                saved.getPublicId(),
                EntityStatus.ACTIVE.name(),
                saved.getCreatedDate()
        );
    }

    /**
     * Generates a short prefixed public identifier: {@code wh_} + 12 hex-safe characters.
     */
    private static String generatePublicId() {
        return "wh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Returns the events string to persist. Defaults to "sms.finalized" when the list
     * is null or empty.
     */
    private static String resolveEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return "sms.finalized";
        }
        return String.join(",", events);
    }
}
