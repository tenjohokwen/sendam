package com.softropic.sendam.client.contract.exception;

import com.softropic.sendam.common.exception.ErrorCode;

public enum ClientError implements ErrorCode {
    INSUFFICIENT_CLIENT_BALANCE,
    DUPLICATE_TRANSACTION_ID,
    TOPUP_ALREADY_PROCESSED;

    @Override
    public String getErrorCode() {
        return this.name();
    }
}
