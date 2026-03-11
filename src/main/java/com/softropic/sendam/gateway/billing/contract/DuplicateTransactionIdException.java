package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.gateway.account.contract.ClientError;
import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when a client submits a top-up with a transaction_id that already exists for that client.
 * Handled by ApiAdvice with HTTP 409 and error_code DUPLICATE_TRANSACTION_ID.
 */
public class DuplicateTransactionIdException extends ApplicationException {

    public DuplicateTransactionIdException(String message) {
        super(message, ClientError.DUPLICATE_TRANSACTION_ID);
    }
}
