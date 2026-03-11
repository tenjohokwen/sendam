package com.softropic.sendam.gateway.auth.api;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.contract.ApiKeyDto;
import com.softropic.sendam.gateway.auth.contract.CreateKeyRequest;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin API key management endpoints. Restricted to ROLE_ADMIN at the filter chain level
 * (AppEndpoints.ADMIN_API_KEYS).
 */
@RestController
@RequestMapping("/api/admin/clients/{clientId}/keys")
public class AdminApiKeyResource {

    private final ApiKeyService apiKeyService;

    public AdminApiKeyResource(final ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /** Create a new API key for a client. Raw value shown once. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreationResult createKey(@PathVariable Long clientId, @RequestBody(required = false) CreateKeyRequest request) {
        String label = request != null ? request.label() : null;
        return apiKeyService.createKey(clientId, label, AuditEventType.ADMIN_API_KEY_CREATED);
    }

    /** List all keys for a client (no raw values). */
    @GetMapping
    public List<ApiKeyDto> listKeys(@PathVariable Long clientId) {
        return apiKeyService.listKeys(clientId);
    }

    /** Revoke a client's API key. */
    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable Long clientId, @PathVariable Long keyId) {
        apiKeyService.revokeKey(clientId, keyId, AuditEventType.ADMIN_API_KEY_REVOKED);
    }
}
