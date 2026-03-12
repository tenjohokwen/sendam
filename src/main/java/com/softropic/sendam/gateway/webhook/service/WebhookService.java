package com.softropic.sendam.gateway.webhook.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.webhook.contract.*;
import com.softropic.sendam.gateway.webhook.repo.WebhookDelivery;
import com.softropic.sendam.gateway.webhook.repo.WebhookDeliveryRepository;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpoint;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpointRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for webhook registration and delivery dispatch.
 */
@Service
@Transactional
@Slf4j
public class WebhookService {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RestTemplate webhookRestTemplate;

    public WebhookService(WebhookEndpointRepository webhookEndpointRepository,
                          WebhookDeliveryRepository webhookDeliveryRepository,
                          ApplicationEventPublisher applicationEventPublisher,
                          @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.webhookRestTemplate = webhookRestTemplate;
    }

    @Transactional(readOnly = true)
    public WebhookHealthResponse getWebhookHealth() {
        WebhookStatsRow row = webhookDeliveryRepository.findWebhookStats();
        return new WebhookHealthResponse(
            row.getTotalAttempts(),
            row.getFailureCount(),
            row.getExhaustedCount()
        );
    }

    /**
     * Registers or updates a client webhook endpoint (upsert semantics).
     */
    public RegisterWebhookResponse register(Long clientId, RegisterWebhookRequest request) {
        return webhookEndpointRepository.findByClientId(clientId)
                .map(existing -> updateExisting(existing, request))
                .orElseGet(() -> createNew(clientId, request));
    }

    // -------------------------------------------------------------------------
    // Delivery dispatch — scheduled poller
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchPendingDeliveries() {
        Instant now = Instant.now();
        List<WebhookDelivery> due = webhookDeliveryRepository
                .findByAttemptStatusAndNextAttemptAtBefore(WebhookDeliveryStatus.PENDING, now);

        for (WebhookDelivery delivery : due) {
            try {
                dispatchOne(delivery);
            } catch (Exception e) {
                log.error("Unexpected error dispatching webhook delivery id={}", delivery.getId(), e);
                applyFailure(delivery, null);
            }
        }
    }

    private void dispatchOne(WebhookDelivery delivery) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(delivery.getWebhookEndpointId())
                .orElse(null);
        if (endpoint == null || endpoint.getStatus() != EntityStatus.ACTIVE) {
            delivery.setAttemptStatus(WebhookDeliveryStatus.EXHAUSTED);
            webhookDeliveryRepository.save(delivery);
            log.info("Webhook delivery id={} EXHAUSTED — endpoint {} no longer active",
                    delivery.getId(), delivery.getWebhookEndpointId());
            return;
        }

        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastAttemptAt(Instant.now());

        try {
            ResponseEntity<String> response = postWebhook(endpoint.getUrl(), delivery.getPayload());
            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setAttemptStatus(WebhookDeliveryStatus.DELIVERED);
                delivery.setHttpStatus(response.getStatusCode().value());
                webhookDeliveryRepository.save(delivery);
                log.info("Webhook delivery id={} DELIVERED to {} (attempt {})",
                        delivery.getId(), endpoint.getUrl(), delivery.getAttemptCount());
            } else {
                delivery.setHttpStatus(response.getStatusCode().value());
                applyFailure(delivery, response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            log.warn("Webhook delivery id={} HTTP error on attempt {}: {}",
                    delivery.getId(), delivery.getAttemptCount(), e.getMessage());
            applyFailure(delivery, null);
        }
    }

    private void applyFailure(WebhookDelivery delivery, Integer httpStatus) {
        if (httpStatus != null) {
            delivery.setHttpStatus(httpStatus);
        }
        int nextAttemptNumber = delivery.getAttemptCount() + 1;
        Instant next = nextAttemptAt(nextAttemptNumber);
        if (next == null) {
            delivery.setAttemptStatus(WebhookDeliveryStatus.EXHAUSTED);
            log.warn("Webhook delivery id={} EXHAUSTED after {} attempts",
                    delivery.getId(), delivery.getAttemptCount());
        } else {
            delivery.setNextAttemptAt(next);
        }
        webhookDeliveryRepository.save(delivery);
    }

    private static Instant nextAttemptAt(int nextAttemptNumber) {
        return switch (nextAttemptNumber) {
            case 2 -> Instant.now().plusSeconds(60);
            case 3 -> Instant.now().plusSeconds(300);
            case 4 -> Instant.now().plusSeconds(1_800);
            case 5 -> Instant.now().plusSeconds(7_200);
            default -> null;
        };
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 500, multiplier = 2.0)
    )
    public ResponseEntity<String> postWebhook(String url, String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        return webhookRestTemplate.postForEntity(url, entity, String.class);
    }

    @Recover
    public ResponseEntity<String> postWebhookFallback(RestClientException e, String url, String payload) {
        log.warn("postWebhook inner retries exhausted for url={}: {}", url, e.getMessage());
        throw new RestClientException("Webhook HTTP call failed after inner retries: " + e.getMessage()) {};
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
        applicationEventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.WEBHOOK_UPDATED,
            existing.getClientId(),
            "client:" + existing.getClientId(),
            "Webhook updated: publicId=" + saved.getPublicId()
        ));
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
        applicationEventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.WEBHOOK_REGISTERED,
            clientId,
            "client:" + clientId,
            "Webhook registered: publicId=" + saved.getPublicId()
        ));
        return new RegisterWebhookResponse(
                saved.getPublicId(),
                EntityStatus.ACTIVE.name(),
                saved.getCreatedDate()
        );
    }

    private static String generatePublicId() {
        return "wh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String resolveEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return "sms.finalized";
        }
        return String.join(",", events);
    }
}
