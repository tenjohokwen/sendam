package com.softropic.sendam.gateway.billing.contract;

import com.softropic.sendam.common.exception.ErrorCode;

public enum PlatformError implements ErrorCode {
    INSUFFICIENT_PLATFORM_BALANCE,
    PLATFORM_FROZEN;

    @Override
    public String getErrorCode() {
        return this.name();
    }
}
