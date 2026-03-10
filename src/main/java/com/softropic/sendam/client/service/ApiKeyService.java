package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.ApiKeyCreationResult;
import com.softropic.sendam.client.repo.ClientApiKeyEntity;
import com.softropic.sendam.client.repo.ClientApiKeyRepository;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.security.contract.exception.AuthorizationException;
import com.softropic.sendam.security.contract.exception.SecurityError;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${apikey.pepper}")
    private String serverPepper;

    public ApiKeyService(final ClientApiKeyRepository repository) {
        this.repository = repository;
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
}
