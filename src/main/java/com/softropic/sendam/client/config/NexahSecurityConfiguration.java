package com.softropic.sendam.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityFilterChain at @Order(0) that permits /v1/provider/** without API key authentication.
 *
 * This chain MUST run before ClientSecurityConfiguration (@Order(1)) so that Nexah DR
 * callback POSTs are not intercepted by the API key filter.
 *
 * IMPORTANT: securityMatcher is scoped ONLY to "/v1/provider/**" — it does NOT shadow
 * /v1/sms/**, /v1/credits/**, or any other client path that requires API key auth.
 */
@Configuration
@Order(0)
public class NexahSecurityConfiguration {

    @Bean
    @Order(0)
    public SecurityFilterChain nexahProviderSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/provider/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
