package com.softropic.sendam.gateway.webhook.api;

import com.softropic.sendam.gateway.webhook.contract.WebhookHealthResponse;
import com.softropic.sendam.gateway.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin health endpoints for Webhooks.
 */
@RestController
@RequestMapping("/api/admin/health/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminWebhookHealthResource {

    private final WebhookService webhookService;

    @GetMapping("/stats")
    public ResponseEntity<WebhookHealthResponse> getWebhookHealth() {
        return ResponseEntity.ok(webhookService.getWebhookHealth());
    }
}
