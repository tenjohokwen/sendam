package com.softropic.sendam.client.infrastructure.filter;

import com.softropic.sendam.client.service.ApiKeyService;
import com.softropic.sendam.security.contract.exception.AuthorizationException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Validates Bearer API keys for /v1/api/** requests.
 * NOT annotated with @Component to avoid Spring Boot auto-registration as a global servlet filter.
 * Instantiated manually in ClientSecurityConfiguration.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService,
                                      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.apiKeyService = apiKeyService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            // No Bearer token — let Spring Security's access denied handler reject
            chain.doFilter(request, response);
            return;
        }
        String rawKey = header.substring(7);
        try {
            Authentication auth = apiKeyService.authenticate(rawKey);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (AuthorizationException e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
