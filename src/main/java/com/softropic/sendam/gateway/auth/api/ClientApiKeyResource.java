package com.softropic.sendam.gateway.auth.api;

import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.contract.ApiKeyDto;
import com.softropic.sendam.gateway.auth.contract.CreateKeyRequest;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;
import com.softropic.sendam.security.contract.util.RateLimited;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for API key self-service operations (APIKEY-01, APIKEY-02, APIKEY-03).
 * Endpoint path /v1/api/keys is protected by the @Order(1) API-key security chain from Plan 02.
 */
@RestController
@RequestMapping("/v1/api/keys")
public class ClientApiKeyResource {

    private final ApiKeyService apiKeyService;

    public ClientApiKeyResource(final ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    private Long getClientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // ApiKeyAuthenticationFilter sets principal = clientId (Long)
        return (Long) auth.getPrincipal();
    }

    /** APIKEY-01: Create a new API key. Raw value shown once. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited(key = "api_req", capacity = 10, duration = 1, unit = TimeUnit.SECONDS)
    public ApiKeyCreationResult createKey(@RequestBody(required = false) CreateKeyRequest request) {
        String label = request != null ? request.label() : null;
        return apiKeyService.createKey(getClientId(), label);
    }

    /** APIKEY-02: List keys (no raw values). */
    @GetMapping
    @RateLimited(key = "api_req", capacity = 10, duration = 1, unit = TimeUnit.SECONDS)
    public List<ApiKeyDto> listKeys() {
        return apiKeyService.listKeys(getClientId());
    }

    /** APIKEY-03: Revoke a key. */
    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited(key = "api_req", capacity = 10, duration = 1, unit = TimeUnit.SECONDS)
    public void revokeKey(@PathVariable Long keyId) {
        apiKeyService.revokeKey(getClientId(), keyId);
    }
}
