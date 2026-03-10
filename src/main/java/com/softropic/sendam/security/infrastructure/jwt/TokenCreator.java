package com.softropic.sendam.security.infrastructure.jwt;

import com.softropic.sendam.security.contract.Principal;
import java.util.Map;

public interface TokenCreator {
    String generateToken(Principal principal, Long dbRefreshToken, boolean isLoggedIn, String seed);
    String generateTokenFromClaims(Map<String, Object> claims);
    Map<String, Object> toClaims(Principal principal, Long dbRefreshToken, boolean isLoggedIn, String seed);
}
