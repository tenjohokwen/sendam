package com.softropic.sendam.gateway.health.api;

import com.softropic.sendam.gateway.health.contract.CircuitBreakerHealthResponse;
import com.softropic.sendam.gateway.health.contract.ProviderStatsResponse;
import com.softropic.sendam.gateway.health.contract.WebhookHealthResponse;
import com.softropic.sendam.gateway.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/health")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminHealthResource {

    private final HealthService healthService;

    @GetMapping("/circuit-breaker")
    public ResponseEntity<CircuitBreakerHealthResponse> getCircuitBreakerHealth() {
        return ResponseEntity.ok(healthService.getCircuitBreakerHealth());
    }

    @GetMapping("/webhook-stats")
    public ResponseEntity<WebhookHealthResponse> getWebhookHealth() {
        return ResponseEntity.ok(healthService.getWebhookHealth());
    }

    @GetMapping("/provider-stats")
    public ResponseEntity<ProviderStatsResponse> getProviderStats() {
        return ResponseEntity.ok(healthService.getProviderStats());
    }
}
