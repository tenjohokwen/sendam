package com.softropic.sendam.gateway.auth.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.repo.ClientApiKeyEntity;
import com.softropic.sendam.gateway.auth.repo.ClientApiKeyRepository;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.security.contract.exception.AuthorizationException;

import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ClientApiKeyRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private static final String PEPPER = "test-pepper";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(apiKeyService, "serverPepper", PEPPER);
    }

    @Test
    @DisplayName("createKey: success - generates raw key and persists hash")
    void createKey_success() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyCreationResult result = apiKeyService.createKey(1L, "Test Key", AuditEventType.CLIENT_API_KEY_CREATED);

        assertThat(result.rawKey()).startsWith("snd_");
        verify(repository).save(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("authenticate: success - valid raw key returns authentication")
    void authenticate_success() {
        String rawKey = "snd_1111111111111111secretpart";
        String secret = "secretpart";
        String expectedHash = HmacUtils.hmacSha256Hex(PEPPER, secret);

        ClientApiKeyEntity entity = new ClientApiKeyEntity();
        entity.setClientId(1L);
        entity.setKeyHash(expectedHash);
        entity.setStatus(EntityStatus.ACTIVE);

        when(repository.findByKeyPrefix(anyString())).thenReturn(Optional.of(entity));

        Authentication auth = apiKeyService.authenticate(rawKey);

        assertThat(auth.getPrincipal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("authenticate: failure - revoked key throws AuthorizationException")
    void authenticate_revoked() {
        String rawKey = "snd_2222222222222222secretpart";
        ClientApiKeyEntity entity = new ClientApiKeyEntity();
        entity.setStatus(EntityStatus.INACTIVE);

        when(repository.findByKeyPrefix(anyString())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> apiKeyService.authenticate(rawKey))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("authenticate: failure - malformed key throws AuthorizationException")
    void authenticate_malformed() {
        assertThatThrownBy(() -> apiKeyService.authenticate("too-short"))
                .isInstanceOf(AuthorizationException.class);
    }
}
