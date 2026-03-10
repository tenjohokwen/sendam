package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.CreateClientRequest;
import com.softropic.sendam.client.contract.CreateClientResponse;
import com.softropic.sendam.client.repo.ClientApiKeyEntity;
import com.softropic.sendam.client.repo.ClientApiKeyRepository;
import com.softropic.sendam.client.repo.ClientEntity;
import com.softropic.sendam.client.repo.ClientRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientApiKeyRepository apiKeyRepository;

    public ClientService(final ClientRepository clientRepository,
                         final ClientApiKeyRepository apiKeyRepository) {
        this.clientRepository = clientRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    public CreateClientResponse createClient(final CreateClientRequest request) {
        ClientEntity client = ClientEntity.builder()
            .name(request.name())
            .status(EntityStatus.ACTIVE)
            .build();
        clientRepository.save(client);

        // Stub: Plan 02 replaces this with ApiKeyService.generateAndPersist(clientId, label)
        String rawKey = "STUB_REPLACE_IN_PLAN_02";
        ClientApiKeyEntity keyEntity = ClientApiKeyEntity.builder()
            .clientId(client.getId())
            .keyPrefix("snd_stub_prefix")
            .keyHash("stub_hash")
            .label(request.keyLabel())
            .status(EntityStatus.ACTIVE)
            .build();
        apiKeyRepository.save(keyEntity);

        return new CreateClientResponse(client.getId(), keyEntity.getId(), rawKey);
    }
}
