package com.softropic.sendam.security.contract.exception;


import com.softropic.sendam.common.exception.ErrorCode;

public enum EncryptionError implements ErrorCode {
    MISSING_SECRET,
    MISSING_TEXT,
    ENCRYPTION_ERROR,
    DECRYPTION_ERROR;

    @Override
    public String getErrorCode() {
        return this.name();
    }
}
