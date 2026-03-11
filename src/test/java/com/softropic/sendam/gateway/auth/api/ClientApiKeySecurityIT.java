package com.softropic.sendam.gateway.auth.api;

import com.softropic.sendam.common.HttpTestClient;
import com.softropic.sendam.config.TestConfig;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

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

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @LocalServerPort
    int randomServerPort;

    private static final String CLIENT_API_KEYS_URL = "/v1/api/keys";
    private static final Long TEST_CLIENT_ID = 999_001L;
    private String rawKey;

    @BeforeEach
    void seedClient() {
        transactionTemplate.execute(status -> {
            // Seed the JWT secret used by SecurityAdviceFilter (global filter on all requests).
            // Uses ON CONFLICT DO NOTHING so repeated @BeforeEach calls are idempotent;
            // JwtSecretService caches the secret after first fetch, so subsequent tests
            // never hit the DB again even if this row were removed.
            jdbcTemplate.update(
                "INSERT INTO main.sec (id, created_by, created_date, last_modified_by, last_modified_date, " +
                "request_id, session_id, status, bus_id, value, version) " +
                "VALUES ('659287191260154475', 'SYSTEM_ACCOUNT', '2024-12-24 06:51:55.357352', " +
                "'SYSTEM_ACCOUNT', '2024-12-24 06:51:55.357352', " +
                "'bed78f34-3e09-4fa8-81db-32326a528cca', NULL, 'ACTIVE', 'jot', " +
                "'loiI8oT2C1tWecrNXPDjN8fveYEU8rD6nb1k1NbVy92rwdd4/KO+aHhXh3A5zjsT5eSFL/xI+9Rqyj4RI6QCiFywn5nZLIwHGPNEY0F9lnDnGGmVjv/9rO5fgGt83+cxNDyGoCePaVEpBd7xHxyDdfpAoLxQs8mhKGqcEsh09Q+26qEiEm/a9bgDSbSQ0sX00VHBLd35OLmvN+ydjEluYxBTa6KzGb2CQ6Ttg4ZaELmbZOWpEjQ1Z7BbbYiXmWyaY+2HnkyhONoGbUpvVKl1c4e9IlQzeUYkekbUbADIm2LNK9Nhfv5/L5esvFrdVOUcUpLk/y8UT9f5xOMLFJ4Ct6s0eTKvNqYkSz2DFRI8Ip4p/ns6gA4V/1MUf9GeqPUWLiOa28Vw15+R8ycUMqb8NZHOP1oj9RunhSwA7EY84bZL3+yePc3n1b8ne8xzaYVEdK1WBu3J6s2AoBaOL/JLWfu8MuxXI+ub', 'v1') " +
                "ON CONFLICT (version, bus_id) DO NOTHING"
            );
            jdbcTemplate.update(
                "INSERT INTO main.client_account (id, name, status, created_by, created_date, last_modified_by, last_modified_date, request_id) " +
                "VALUES (?, 'IT Test Client', 'ACTIVE', 'test', NOW(), 'test', NOW(), 'test-req')",
                TEST_CLIENT_ID
            );
            return null;
        });
        // ApiKeyService.createKey() runs in its own @Transactional — client_account row must exist first.
        ApiKeyCreationResult result = apiKeyService.createKey(TEST_CLIENT_ID, "it-test-key", AuditEventType.ADMIN_API_KEY_CREATED);
        rawKey = result.rawKey();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.execute(status -> {
            jdbcTemplate.update("DELETE FROM main.client_api_key WHERE client_id = ?", TEST_CLIENT_ID);
            jdbcTemplate.update("DELETE FROM main.client_account WHERE id = ?", TEST_CLIENT_ID);
            return null;
        });
    }

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

    /**
     * GET /v1/api/keys with a valid Bearer API key must return 200.
     *
     * Proves the full authentication round-trip:
     *   1. ApiKeyAuthenticationFilter extracts the raw key from the Bearer token.
     *   2. ApiKeyService.authenticate() hashes the secret with the server pepper and matches
     *      the stored hash in client_api_key.
     *   3. The filter sets clientId as the Authentication principal.
     *   4. ClientApiKeyResource.listKeys() is invoked and returns HTTP 200 with a JSON array.
     */
    @Test
    void clientApiKeysWithValidTokenReturns200() {
        String url = baseUrl() + CLIENT_API_KEYS_URL;

        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(rawKey);

        ResponseEntity<List> response = httpTestClient.makeHttpRequest(
            url, HttpMethod.GET, null, headers, List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
