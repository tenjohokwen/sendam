package com.softropic.sendam.client.infrastructure.nexah;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softropic.sendam.client.contract.exception.ProviderUnavailableException;
import com.softropic.sendam.client.contract.nexah.NexahProperties;
import com.softropic.sendam.client.contract.nexah.NexahSendRequest;
import com.softropic.sendam.client.contract.nexah.NexahSendResponse;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Outbound HTTP client for the Nexah BulkSMS provider.
 *
 * Does NOT extend AbstractClient — AbstractClient requires a RestRequestInterceptor and
 * heartbeatPath designed for the MoMo auth model. NexahClient uses credential-in-body auth
 * and manages its own RestTemplate directly.
 *
 * Circuit breaker: the 'nexah' circuit breaker (configured in application.yaml) wraps
 * sendSms(). If the circuit is OPEN or a call fails, the fallback throws
 * ProviderUnavailableException -> HTTP 503 via ApiAdvice.
 *
 * checkAvailability() is the probe used by health checks — it is NOT wrapped by the
 * circuit breaker itself (it IS the probe, not the guarded operation).
 */
@Slf4j
@Component
public class NexahClient {

    private final RestTemplate restTemplate;
    private final NexahProperties properties;

    public NexahClient(NexahProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate(
                new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
        this.restTemplate.setMessageConverters(messageConverters());
    }

    /**
     * Send an SMS via the Nexah sendsms endpoint.
     * Wrapped by the 'nexah' Resilience4j circuit breaker.
     * Falls back to sendSmsFallback on failure or open circuit.
     *
     * @param request the send request containing credentials and message details
     * @return parsed NexahSendResponse
     * @throws ProviderUnavailableException if the circuit is open or call fails after breaker trips
     */
    @CircuitBreaker(name = "nexah", fallbackMethod = "sendSmsFallback")
    public NexahSendResponse sendSms(NexahSendRequest request) {
        final String url = properties.baseUrl() + "/sendsms";
        log.info("Sending SMS via Nexah to {} recipients", countRecipients(request.mobiles()));
        return restTemplate.postForObject(url, request, NexahSendResponse.class);
    }

    /**
     * Fallback invoked when the 'nexah' circuit breaker is OPEN or when sendSms throws.
     * Always throws ProviderUnavailableException.
     */
    private NexahSendResponse sendSmsFallback(NexahSendRequest request, Throwable t) {
        log.warn("Nexah circuit breaker triggered. Cause: {}", t.getMessage());
        throw new ProviderUnavailableException(
                "Nexah SMS provider is unavailable. Circuit breaker active. Cause: " + t.getMessage());
    }

    /**
     * Probe to check Nexah availability by calling the smscredit endpoint.
     * Used by health indicators — NOT wrapped by the circuit breaker.
     *
     * @return true if Nexah responds with responseCode == 1; false otherwise
     */
    public boolean checkAvailability() {
        try {
            final String url = properties.baseUrl() + "/smscredit";
            final Map<String, String> body = Map.of(
                    "user", properties.user(),
                    "password", properties.password()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response == null) {
                return false;
            }
            Object code = response.get("responsecode");
            return Integer.valueOf(1).equals(code) || "1".equals(String.valueOf(code));
        } catch (RestClientException e) {
            log.warn("Nexah availability check failed: {}", e.getMessage());
            return false;
        }
    }

    private List<HttpMessageConverter<?>> messageConverters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setObjectMapper(new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        converters.add(jsonConverter);
        return converters;
    }

    private int countRecipients(String mobiles) {
        if (mobiles == null || mobiles.isBlank()) return 0;
        return mobiles.split(",").length;
    }
}
