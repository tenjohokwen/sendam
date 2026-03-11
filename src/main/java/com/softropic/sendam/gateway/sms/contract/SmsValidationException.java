package com.softropic.sendam.gateway.sms.contract;

import com.softropic.sendam.common.exception.ApplicationException;

/**
 * Thrown for SMS input validation failures (INVALID_PHONE_NUMBER, INVALID_SENDER_ID,
 * INVALID_SCHEDULE_TIME). Handled by ApiAdvice with HTTP 400.
 */
public class SmsValidationException extends ApplicationException {

    public SmsValidationException(String message, SmsError errorCode) {
        super(message, errorCode);
    }
}
