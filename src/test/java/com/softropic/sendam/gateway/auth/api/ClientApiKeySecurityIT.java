package com.softropic.sendam.gateway.auth.api;

import com.softropic.sendam.common.HttpTestClient;
import com.softropic.sendam.config.TestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test confirming that /v1/api/keys is handled by the @Order(1) API key security chain.
 *
 * Before the fix, ClientSecurityConfiguration's securityMatcher did not include /v1/api/**,
 * so requests to /v1/api/keys fell through to the @Order(2) JWT chain and received 403.
 * After the fix they are correctly handled by the API key chain and receive 401.
 */
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"ledger.database.spy=true", "custom.flyway.check-schema=false"})
@Import(TestConfig.class)
@TestPropertySource(properties = "spring.cloud.compatibility-verifier.enabled=false")
public class ClientApiKeySecurityIT {

    @Autowired
    private HttpTestClient httpTestClient;

    @LocalServerPort
    int randomServerPort;

    private static final String CLIENT_API_KEYS_URL = "/v1/api/keys";

    private String baseUrl() {
        return "http://localhost:" + randomServerPort;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * GET /v1/api/keys with no Authorization header must return 401.
     *
     * Proves the path is now claimed by the @Order(1) API key chain
     * (which returns 401 via AuthenticationExceptionHandler) rather than
     * falling through to the @Order(2) JWT chain (which would have returned 403).
     */
    @Test
    void clientApiKeysWithNoTokenReturns401() {
        String url = baseUrl() + CLIENT_API_KEYS_URL;

        assertThatThrownBy(() ->
                httpTestClient.makeHttpRequest(url, HttpMethod.GET, null, jsonHeaders(), Object.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> {
                    HttpClientErrorException ex = (HttpClientErrorException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    /**
     * GET /v1/api/keys with an invalid Bearer token must return 401.
     *
     * Proves ApiKeyAuthenticationFilter is invoked and rejects the bad key.
     */
    @Test
    void clientApiKeysWithRevokedOrInvalidTokenReturns401() {
        String url = baseUrl() + CLIENT_API_KEYS_URL;

        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth("this-is-not-a-valid-api-key");

        assertThatThrownBy(() ->
                httpTestClient.makeHttpRequest(url, HttpMethod.GET, null, headers, Object.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> {
                    HttpClientErrorException ex = (HttpClientErrorException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
