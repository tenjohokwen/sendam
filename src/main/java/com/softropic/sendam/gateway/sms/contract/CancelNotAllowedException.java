package com.softropic.sendam.gateway.sms.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown when cancel is attempted on a non-ACCEPTED or non-scheduled request.
 * Handled by ApiAdvice with HTTP 409.
 */
public class CancelNotAllowedException extends ApplicationException {

    public CancelNotAllowedException(String message) {
        super(message, SmsError.CANCEL_NOT_ALLOWED);
    }
}
