package com.softropic.sendam.security.contract.exception;


import com.softropic.sendam.common.exception.ApplicationException;
import com.softropic.sendam.common.exception.ErrorCode;

public class EncryptionException extends ApplicationException {
    public EncryptionException(String msg,
                               ErrorCode errorCode) {
        super(msg, errorCode);
    }

    public EncryptionException(String msg, Throwable cause, ErrorCode errorCode) {
        super(msg, cause, errorCode);
    }
}
