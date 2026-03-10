package com.softropic.sendam.client.contract.nexah;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nexah upstream provider configuration properties.
 * Bound from YAML prefix "nexah".
 * Credentials are injected via environment variables NEXAH_USER, NEXAH_PASSWORD, NEXAH_SENDERID.
 */
@ConfigurationProperties(prefix = "nexah")
public record NexahProperties(
        String user,
        String password,
        String senderid,
        String baseUrl
) {
}
