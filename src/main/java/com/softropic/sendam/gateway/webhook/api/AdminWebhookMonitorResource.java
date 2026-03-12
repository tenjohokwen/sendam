package com.softropic.sendam.gateway.webhook.api;

import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryRow;
import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.gateway.webhook.contract.WebhookEndpointRow;
import com.softropic.sendam.gateway.webhook.service.AdminWebhookMonitorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminWebhookMonitorResource {

    private final AdminWebhookMonitorService adminWebhookMonitorService;

    /**
     * WEBH-01: Returns paginated list of all registered webhook endpoints.
     */
    @GetMapping("/endpoints")
    public ResponseEntity<Page<WebhookEndpointRow>> getEndpoints(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminWebhookMonitorService.getEndpoints(pageable));
    }

    /**
     * WEBH-02: Returns paginated webhook delivery records.
     * Optional filters: clientId, attemptStatus (PENDING/DELIVERED/FAILED/EXHAUSTED).
     */
    @GetMapping("/deliveries")
    public ResponseEntity<Page<WebhookDeliveryRow>> getDeliveries(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) WebhookDeliveryStatus attemptStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
            adminWebhookMonitorService.getDeliveries(clientId, attemptStatus, pageable));
    }
}
