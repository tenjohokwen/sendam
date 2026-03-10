package com.softropic.sendam.client.contract.exception;

import com.softropic.sendam.common.exception.ErrorCode;

public enum SmsError implements ErrorCode {
    INVALID_PHONE_NUMBER,
    INVALID_SENDER_ID,
    INVALID_SCHEDULE_TIME,
    CANCEL_NOT_ALLOWED;

    @Override
    public String getErrorCode() {
        return this.name();
    }
}
