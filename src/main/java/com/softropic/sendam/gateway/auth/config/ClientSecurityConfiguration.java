package com.softropic.sendam.gateway.auth.config;

import com.softropic.sendam.gateway.auth.config.ApiKeyAuthenticationFilter;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;
import com.softropic.sendam.security.config.AppEndpoints;
import com.softropic.sendam.security.infrastructure.ApplicationAccessDeniedHandler;
import com.softropic.sendam.security.infrastructure.AuthenticationExceptionHandler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Second SecurityFilterChain scoped to client-facing API paths.
 * Runs at @Order(1) so it claims these paths before the JWT chain (@Order(2)).
 * Authenticates requests using Bearer API keys via ApiKeyAuthenticationFilter.
 * The @Order(2) JWT chain uses /api/** and other admin paths — it will not interfere with /v1/** paths.
 */
@Configuration
@Order(1)
public class ClientSecurityConfiguration {

    @Bean
    @Order(1)
    public SecurityFilterChain clientApiSecurityFilterChain(
            HttpSecurity http,
            ApiKeyService apiKeyService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) throws Exception {

        ApiKeyAuthenticationFilter apiKeyFilter =
            new ApiKeyAuthenticationFilter(apiKeyService, handlerExceptionResolver);

        http
            .securityMatcher(AppEndpoints.SMS_API, AppEndpoints.CREDITS_API,
                             AppEndpoints.CLIENT_API_KEYS, AppEndpoints.WEBHOOKS_API,
                             AppEndpoints.SMS_ANALYTICS)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new AuthenticationExceptionHandler(handlerExceptionResolver))
                .accessDeniedHandler(new ApplicationAccessDeniedHandler(handlerExceptionResolver)))
            .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        return http.build();
    }
}
