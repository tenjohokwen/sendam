package com.softropic.sendam.gateway.provider.nexah.contract;

import com.softropic.sendam.common.exception.ErrorCode;

/**
 * Error codes for provider integration failures.
 * PROVIDER_UNAVAILABLE is emitted when the Nexah circuit breaker is OPEN
 * and mapped to HTTP 503 by ApiAdvice.
 */
public enum ProviderError implements ErrorCode {
    PROVIDER_UNAVAILABLE;

    @Override
    public String getErrorCode() {
        return this.name();
    }
}
