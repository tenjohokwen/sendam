package com.softropic.sendam.security.infrastructure.jwt;

import com.softropic.sendam.security.contract.exception.AuthorizationException;
import jakarta.servlet.http.HttpServletRequest;

public interface TokenValidator {
    boolean isTokenFixed(HttpServletRequest request);
    boolean hasDbRefreshTokenExpired(HttpServletRequest request);
    void ensureClientHasPreLoginId();
    void ensureClientHasPostLoginId();
    void ensureAuthTokenPresent(HttpServletRequest request) throws AuthorizationException;
}
