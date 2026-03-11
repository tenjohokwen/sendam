package com.softropic.sendam.gateway.auth.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.contract.ApiKeyDto;
import com.softropic.sendam.gateway.auth.repo.ClientApiKeyEntity;
import com.softropic.sendam.gateway.auth.repo.ClientApiKeyRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.security.contract.exception.AuthorizationException;
import com.softropic.sendam.security.contract.exception.SecurityError;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class ApiKeyService {

    private final ClientApiKeyRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${apikey.pepper}")
    private String serverPepper;

    public ApiKeyService(final ClientApiKeyRepository repository,
                         final ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ApiKeyCreationResult generateAndPersist(Long clientId, String label) {
        SecureRandom rng = new SecureRandom();

        // Prefix: 8 random bytes -> 16 hex chars
        byte[] prefixBytes = new byte[8];
        rng.nextBytes(prefixBytes);
        String hexPrefix = HexFormat.of().formatHex(prefixBytes); // 16 chars

        // Secret: 32 random bytes -> base64url without padding (~43 chars)
        byte[] secretBytes = new byte[32];
        rng.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        // Full raw key: "snd_" + 16 hex + 43 base64url = ~63 chars
        String rawKey = "snd_" + hexPrefix + secret;
        String keyPrefixStored = "snd_" + hexPrefix; // 20 chars, stored in DB

        // Hash the secret with HMAC-SHA256 using server pepper
        String keyHash = HmacUtils.hmacSha256Hex(serverPepper, secret);

        ClientApiKeyEntity entity = ClientApiKeyEntity.builder()
            .clientId(clientId)
            .keyPrefix(keyPrefixStored)
            .keyHash(keyHash)
            .label(label)
            .status(EntityStatus.ACTIVE)
            .build();
        repository.save(entity);

        return new ApiKeyCreationResult(entity.getId(), rawKey);
    }

    /** Creates a new API key for the given client and publishes an audit event. */
    @Transactional
    public ApiKeyCreationResult createKey(Long clientId, String label, AuditEventType auditEventType) {
        ApiKeyCreationResult result = generateAndPersist(clientId, label);
        eventPublisher.publishEvent(new DomainAuditEvent(
            auditEventType,
            clientId,
            resolveActor(clientId, auditEventType),
            "API key created: label=" + label
        ));
        return result;
    }

    /** Lists all API keys for a client. Raw key values are never returned. */
    @Transactional(readOnly = true)
    public List<ApiKeyDto> listKeys(Long clientId) {
        return repository.findAllByClientId(clientId).stream()
            .map(k -> new ApiKeyDto(k.getId(), k.getLabel(),
                                    k.getStatus().name(), k.getCreatedDate()))
            .toList();
    }

    /**
     * Revokes the API key and publishes an audit event.
     * Throws ResourceNotFoundException if the key does not exist
     * or does not belong to this client (prevents cross-client revocation).
     * Revoked keys immediately fail auth on next request — no caching means
     * the next DB read sees EntityStatus.INACTIVE (AUTH-04).
     */
    @Transactional
    public void revokeKey(Long clientId, Long keyId, AuditEventType auditEventType) {
        ClientApiKeyEntity key = repository.findById(keyId)
            .orElseThrow(() -> new ResourceNotFoundException("API key not found", "api-key"));
        if (!key.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("API key not found", "api-key");  // obscure ownership
        }
        key.setStatus(EntityStatus.INACTIVE);
        repository.save(key);
        eventPublisher.publishEvent(new DomainAuditEvent(
            auditEventType,
            clientId,
            resolveActor(clientId, auditEventType),
            "API key revoked: keyId=" + keyId
        ));
    }

    /**
     * Authenticates a raw Bearer key. Returns a UsernamePasswordAuthenticationToken
     * with clientId as principal and ROLE_API_CLIENT as authority.
     * Throws AuthorizationException on invalid, revoked, or mismatched keys.
     * Never caches — DB read on every call (requirement AUTH-04).
     */
    public Authentication authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("snd_") || rawKey.length() < 20) {
            throw new AuthorizationException("Invalid key format", SecurityError.MISSING_TOKEN);
        }
        String prefix = rawKey.substring(0, 20); // "snd_" + 16 hex chars
        String incomingSecret = rawKey.substring(20);

        ClientApiKeyEntity keyEntity = repository.findByKeyPrefix(prefix)
            .orElseThrow(() -> new AuthorizationException("API key not found", SecurityError.MISSING_TOKEN));

        if (keyEntity.getStatus() != EntityStatus.ACTIVE) {
            throw new AuthorizationException("API key is revoked", SecurityError.MISSING_RIGHTS);
        }

        String expectedHash = HmacUtils.hmacSha256Hex(serverPepper, incomingSecret);
        // Constant-time comparison — prevents timing attacks
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                                   keyEntity.getKeyHash().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthorizationException("API key mismatch", SecurityError.MISSING_TOKEN);
        }

        return new UsernamePasswordAuthenticationToken(
            keyEntity.getClientId(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
        );
    }

    private String resolveActor(Long clientId, AuditEventType type) {
        // Admin types: resolve from security context; client types: use client ID
        if (type == AuditEventType.CLIENT_API_KEY_CREATED || type == AuditEventType.CLIENT_API_KEY_REVOKED) {
            return "client:" + clientId;
        }
        try {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            return auth != null && auth.getName() != null ? auth.getName() : "admin";
        } catch (Exception e) {
            return "admin";
        }
    }
}
