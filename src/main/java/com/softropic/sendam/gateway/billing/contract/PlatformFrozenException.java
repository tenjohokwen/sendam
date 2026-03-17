package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when an operation is attempted while the platform is frozen.
 * Handled by ApiAdvice with HTTP 503 (Service Unavailable) and error_code PLATFORM_FROZEN.
 *
 * <p>Platform freeze is not client-specific — there is no clientId field.
 * Extends ApplicationException so that @Transactional rolls back atomically.
 */
public class PlatformFrozenException extends ApplicationException {

    public PlatformFrozenException(String message) {
        super(message, PlatformError.PLATFORM_FROZEN);
    }
}
