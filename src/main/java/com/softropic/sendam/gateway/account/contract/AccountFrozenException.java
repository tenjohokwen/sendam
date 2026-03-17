package com.softropic.sendam.gateway.account.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when an operation is attempted on a frozen client account.
 * Handled by ApiAdvice with HTTP 423 (Locked) and error_code ACCOUNT_FROZEN.
 *
 * <p>Extends ApplicationException so that:
 * <ul>
 *   <li>@Transactional rolls back atomically on this exception</li>
 *   <li>ApiAdvice picks up getSupportId() and getLogContext() correctly</li>
 * </ul>
 */
public class AccountFrozenException extends ApplicationException {

    private final Long clientId;

    public AccountFrozenException(String message, Long clientId) {
        super(message, ClientError.ACCOUNT_FROZEN);
        this.clientId = clientId;
    }

    public Long getClientId() {
        return clientId;
    }
}
