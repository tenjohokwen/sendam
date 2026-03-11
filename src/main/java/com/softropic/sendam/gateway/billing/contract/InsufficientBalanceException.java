package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.gateway.account.contract.ClientError;
import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when a credit operation would reduce a client's balance below zero.
 * Handled by ApiAdvice with HTTP 400 and error_code INSUFFICIENT_CLIENT_BALANCE.
 */
public class InsufficientBalanceException extends ApplicationException {

    private final Long clientId;
    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientBalanceException(String message, Long clientId, long currentBalance, long requestedAmount) {
        super(message, ClientError.INSUFFICIENT_CLIENT_BALANCE);
        this.clientId = clientId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public Long getClientId() {
        return clientId;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }
}
