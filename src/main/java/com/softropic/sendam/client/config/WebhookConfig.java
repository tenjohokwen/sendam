package com.softropic.sendam.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

/**
 * Webhook-specific Spring configuration.
 *
 * <p>Declares {@code @EnableRetry} here rather than on {@link ClientConfig} so that
 * retry infrastructure is co-located with the RestTemplate it serves.
 *
 * <p>The {@code webhookRestTemplate} bean uses short timeouts (3s connect, 5s read)
 * to bound the worst-case poller latency to n*5s per cycle when client endpoints are slow.
 */
@Configuration
@EnableRetry
public class WebhookConfig {

    @Bean("webhookRestTemplate")
    public RestTemplate webhookRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }
}
