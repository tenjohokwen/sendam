package com.softropic.sendam.gateway.provider.nexah.api;

import com.softropic.sendam.gateway.provider.nexah.contract.CircuitBreakerHealthResponse;
import com.softropic.sendam.gateway.provider.nexah.contract.ProviderStatsResponse;
import com.softropic.sendam.gateway.provider.nexah.service.NexahDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin health endpoints for the Nexah provider.
 */
@RestController
@RequestMapping("/api/admin/health/nexah")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminNexahHealthResource {

    private final NexahDispatchService nexahService;

    @GetMapping("/circuit-breaker")
    public ResponseEntity<CircuitBreakerHealthResponse> getCircuitBreakerHealth() {
        return ResponseEntity.ok(nexahService.getCircuitBreakerHealth());
    }

    @GetMapping("/provider-stats")
    public ResponseEntity<ProviderStatsResponse> getProviderStats() {
        return ResponseEntity.ok(nexahService.getProviderStats());
    }
}
