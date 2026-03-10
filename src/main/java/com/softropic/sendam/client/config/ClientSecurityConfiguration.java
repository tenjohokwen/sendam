package com.softropic.sendam.client.config;

import com.softropic.sendam.client.infrastructure.filter.ApiKeyAuthenticationFilter;
import com.softropic.sendam.client.service.ApiKeyService;
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
 * Second SecurityFilterChain scoped to /v1/api/**.
 * Runs at @Order(1) so it claims these paths before the JWT chain (@Order(2)).
 * Authenticates requests using Bearer API keys via ApiKeyAuthenticationFilter.
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
            .securityMatcher("/v1/api/**")
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
