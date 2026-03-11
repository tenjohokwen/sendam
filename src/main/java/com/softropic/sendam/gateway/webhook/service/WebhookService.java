package com.softropic.sendam.gateway.webhook.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookRequest;
import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookResponse;
import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
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
 *
 * <p>{@link #register(Long, RegisterWebhookRequest)} upserts a webhook endpoint:
 * if the client already has a registered endpoint the URL is updated and the existing
 * {@code public_id} is returned; otherwise a new endpoint is created.
 *
 * <p>{@link #dispatchPendingDeliveries()} is a {@code @Scheduled} poller (fixedDelay=30s)
 * that dispatches PENDING {@link WebhookDelivery} rows whose {@code next_attempt_at} is due.
 * Success advances status to DELIVERED. Failure applies exponential backoff (1min/5min/30min/2h)
 * up to 5 attempts, then EXHAUSTED.
 *
 * <p>{@link #postWebhook(String, String)} is a {@code @Retryable} public method so that
 * Spring's AOP proxy can intercept it for transient inner retries before the outer DB-backed
 * backoff takes effect.
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
    // Delivery dispatch — scheduled poller
    // -------------------------------------------------------------------------

    /**
     * Polls for PENDING webhook delivery rows whose {@code next_attempt_at} is due and
     * dispatches each via HTTP POST to the registered client URL.
     *
     * <p>Uses {@code fixedDelay} (not fixedRate) so the next run only starts after the
     * current run finishes, preventing overlapping executions (same decision as SmsSchedulerService).
     */
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
                // Advance to next backoff without crashing the entire poller
                applyFailure(delivery, null);
            }
        }
    }

    /**
     * Dispatches a single delivery row: loads its endpoint, increments attempt count,
     * calls {@link #postWebhook(String, String)} (which is @Retryable for transient errors),
     * then advances status based on HTTP outcome.
     */
    private void dispatchOne(WebhookDelivery delivery) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(delivery.getWebhookEndpointId())
                .orElse(null);
        if (endpoint == null || endpoint.getStatus() != EntityStatus.ACTIVE) {
            // Endpoint deleted or deactivated — mark exhausted; no point retrying
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

    /**
     * Applies the exponential backoff schedule after a failed delivery attempt.
     *
     * <p>Backoff schedule (based on the NEXT attempt number):
     * <ul>
     *   <li>Attempt 2: +1 min</li>
     *   <li>Attempt 3: +5 min</li>
     *   <li>Attempt 4: +30 min</li>
     *   <li>Attempt 5: +2 hours</li>
     *   <li>Attempt 6+: EXHAUSTED (no further retries)</li>
     * </ul>
     */
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
            // status stays PENDING — poller picks it up again after the backoff window
            delivery.setNextAttemptAt(next);
        }
        webhookDeliveryRepository.save(delivery);
    }

    /**
     * Computes the absolute timestamp for the next attempt, or {@code null} if the
     * delivery should be marked EXHAUSTED (no further attempts).
     */
    private static Instant nextAttemptAt(int nextAttemptNumber) {
        return switch (nextAttemptNumber) {
            case 2 -> Instant.now().plusSeconds(60);        // +1 min
            case 3 -> Instant.now().plusSeconds(300);       // +5 min
            case 4 -> Instant.now().plusSeconds(1_800);     // +30 min
            case 5 -> Instant.now().plusSeconds(7_200);     // +2 hours
            default -> null;                                // EXHAUSTED
        };
    }

    /**
     * Performs the single HTTP POST to the client webhook URL.
     *
     * <p>Annotated {@code @Retryable} for fast inner retries on transient network errors
     * (e.g. connection reset, 500) before the outer DB-backed backoff machinery handles
     * longer waits. Must be {@code public} so the Spring AOP proxy can intercept it.
     *
     * <p>{@code @EnableRetry} is declared on {@link com.softropic.sendam.gateway.webhook.config.WebhookConfig}.
     */
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

    /**
     * Recovery method for {@link #postWebhook(String, String)} — called when all inner
     * retry attempts are exhausted. Re-throws as a {@link RestClientException} so
     * {@code dispatchOne()} catches it and applies the outer backoff.
     */
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
