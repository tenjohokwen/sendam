package com.softropic.sendam.gateway.provider.nexah.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown by NexahClient when the 'nexah' circuit breaker is OPEN or when the upstream
 * Nexah provider call fails and the circuit breaker trips.
 *
 * Handled by ApiAdvice.providerUnavailableHandler() with HTTP 503 SERVICE_UNAVAILABLE
 * and error_code PROVIDER_UNAVAILABLE.
 */
public class ProviderUnavailableException extends ApplicationException {

    public ProviderUnavailableException(String message) {
        super(message, ProviderError.PROVIDER_UNAVAILABLE);
    }
}
