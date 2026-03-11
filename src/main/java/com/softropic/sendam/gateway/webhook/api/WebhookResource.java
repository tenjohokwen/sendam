package com.softropic.sendam.gateway.webhook.api;

import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookRequest;
import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookResponse;
import com.softropic.sendam.gateway.webhook.service.WebhookService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-facing webhook registration endpoint.
 * Protected by the /v1/** API key security chain (@Order(1)).
 * No additional @PreAuthorize needed — the filter chain authenticates via API key.
 *
 * <p>POST /v1/webhooks — registers or updates the client's webhook URL (upsert).
 */
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookResource {

    private final WebhookService webhookService;

    /**
     * Registers or updates the authenticated client's webhook endpoint.
     *
     * <p>On duplicate registration (same client), the URL is updated and the existing
     * {@code webhook_id} is returned — upsert semantics.
     *
     * @param request the webhook registration payload
     * @return 200 with webhook_id, status, and created_at
     */
    @PostMapping
    public ResponseEntity<RegisterWebhookResponse> register(
            @RequestBody @Valid RegisterWebhookRequest request) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RegisterWebhookResponse response = webhookService.register(clientId, request);
        return ResponseEntity.ok(response);
    }
}
