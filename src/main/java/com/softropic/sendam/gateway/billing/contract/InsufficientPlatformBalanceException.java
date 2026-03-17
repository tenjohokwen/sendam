package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when a platform credit operation would reduce the platform balance below zero.
 * Handled by ApiAdvice with HTTP 422 and error_code INSUFFICIENT_PLATFORM_BALANCE.
 *
 * <p>Extends ApplicationException (which extends RuntimeException) so that:
 * <ul>
 *   <li>@Transactional rolls back atomically on this exception</li>
 *   <li>ApiAdvice picks up getSupportId() and getLogContext() correctly</li>
 * </ul>
 */
public class InsufficientPlatformBalanceException extends ApplicationException {

    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientPlatformBalanceException(String message, long currentBalance, long requestedAmount) {
        super(message, PlatformError.INSUFFICIENT_PLATFORM_BALANCE);
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }
}
