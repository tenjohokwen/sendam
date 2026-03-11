package com.softropic.sendam.gateway.account.contract;

import com.softropic.sendam.common.exception.ApplicationException;
import com.softropic.sendam.security.contract.exception.SecurityError;

/**
 * Thrown when the recipient rate limit (1000/min) is exceeded.
 * Handled by ApiAdvice with HTTP 429.
 *
 * <p>Extends {@link ApplicationException} directly (NOT {@link com.softropic.sendam.security.contract.exception.AuthorizationException})
 * so that ApiAdvice can assign it its own 429 handler without interfering with
 * the existing AuthorizationException → HTTP 401 path.
 */
public class RateLimitExceededException extends ApplicationException {

    public RateLimitExceededException(String message) {
        super(message, SecurityError.TOO_MANY_REQUESTS);
    }
}
