package com.softropic.sendam.gateway.auth.api;

import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.auth.contract.ApiKeyDto;
import com.softropic.sendam.gateway.auth.contract.CreateKeyRequest;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminApiKeyResourceTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private AdminApiKeyResource adminApiKeyResource;

    @Test
    @DisplayName("createKey: admin can create key for any client")
    void createKey_success() {
        CreateKeyRequest request = new CreateKeyRequest("Admin Created");
        when(apiKeyService.createKey(100L, "Admin Created")).thenReturn(new ApiKeyCreationResult(1L, "snd_raw"));

        ApiKeyCreationResult result = adminApiKeyResource.createKey(100L, request);

        assertThat(result.rawKey()).isEqualTo("snd_raw");
        verify(apiKeyService).createKey(100L, "Admin Created");
    }

    @Test
    @DisplayName("listKeys: admin can list keys for any client")
    void listKeys_success() {
        ApiKeyDto dto = new ApiKeyDto(1L, "Key 1", "ACTIVE", Instant.now());
        when(apiKeyService.listKeys(100L)).thenReturn(List.of(dto));

        List<ApiKeyDto> results = adminApiKeyResource.listKeys(100L);

        assertThat(results).hasSize(1);
        verify(apiKeyService).listKeys(100L);
    }

    @Test
    @DisplayName("revokeKey: admin can revoke key for any client")
    void revokeKey_success() {
        adminApiKeyResource.revokeKey(100L, 1L);
        verify(apiKeyService).revokeKey(100L, 1L);
    }
}
