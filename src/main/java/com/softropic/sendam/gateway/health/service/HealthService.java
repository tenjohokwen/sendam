package com.softropic.sendam.gateway.health.service;

import com.softropic.sendam.gateway.health.contract.CircuitBreakerHealthResponse;
import com.softropic.sendam.gateway.health.contract.ProviderStatsResponse;
import com.softropic.sendam.gateway.health.contract.ProviderStatsRow;
import com.softropic.sendam.gateway.health.contract.WebhookHealthResponse;
import com.softropic.sendam.gateway.health.contract.WebhookStatsRow;
import com.softropic.sendam.gateway.health.repo.HealthRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class HealthService {

    private final HealthRepository healthRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerHealthResponse getCircuitBreakerHealth() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
        CircuitBreaker.Metrics m = cb.getMetrics();
        return new CircuitBreakerHealthResponse(
            cb.getState().name(),
            m.getFailureRate(),
            m.getNumberOfFailedCalls(),
            m.getNumberOfSuccessfulCalls(),
            m.getNumberOfNotPermittedCalls()
        );
    }

    public WebhookHealthResponse getWebhookHealth() {
        WebhookStatsRow row = healthRepository.findWebhookStats();
        return new WebhookHealthResponse(
            row.getTotalAttempts(),
            row.getFailureCount(),
            row.getExhaustedCount()
        );
    }

    public ProviderStatsResponse getProviderStats() {
        ProviderStatsRow row = healthRepository.findProviderStats();
        double failureRate = row.getDrReceived() == 0
            ? 0.0
            : (double) row.getFailedCount() / row.getDrReceived() * 100.0;
        return new ProviderStatsResponse(
            row.getTotalSubmitted(),
            row.getDrReceived(),
            row.getFailedCount(),
            failureRate
        );
    }
}
