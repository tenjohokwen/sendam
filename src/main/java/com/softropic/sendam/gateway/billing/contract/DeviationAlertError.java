package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.common.exception.ErrorCode;

public enum DeviationAlertError implements ErrorCode {
    ALERT_NOT_FOUND,
    INVALID_STATUS_TRANSITION;

    @Override
    public String getErrorCode() {
        return name();
    }
}
