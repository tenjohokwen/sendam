package com.softropic.sendam.client.contract.exception;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when an admin attempts to approve or reject a top-up that has already been
 * processed (APPROVED or REJECTED). Handled by ApiAdvice with HTTP 409 and error_code
 * TOPUP_ALREADY_PROCESSED.
 */
public class TopupAlreadyProcessedException extends ApplicationException {

    public TopupAlreadyProcessedException(String message) {
        super(message, ClientError.TOPUP_ALREADY_PROCESSED);
    }
}
