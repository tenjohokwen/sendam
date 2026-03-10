package com.softropic.sendam.security.contract.exception;


import static com.softropic.sendam.security.contract.exception.SecurityError.TOKEN_THEFT;

public class JWTTheftException extends AuthorizationException {
    public JWTTheftException(final String msg) {
        super(msg,TOKEN_THEFT);
    }
}
