package com.softropic.sendam.gateway.provider.nexah.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nexah upstream provider configuration properties.
 * Bound from YAML prefix "nexah".
 * Credentials are injected via environment variables NEXAH_USER, NEXAH_PASSWORD.
 */
@ConfigurationProperties(prefix = "nexah")
public record NexahProperties(
        String user,
        String password,
        String baseUrl
) {
}
