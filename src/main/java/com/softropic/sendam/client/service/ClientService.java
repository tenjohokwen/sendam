package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.ApiKeyCreationResult;
import com.softropic.sendam.client.contract.CreateClientRequest;
import com.softropic.sendam.client.contract.CreateClientResponse;
import com.softropic.sendam.client.repo.ClientEntity;
import com.softropic.sendam.client.repo.ClientRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ApiKeyService apiKeyService;

    public ClientService(final ClientRepository clientRepository,
                         final ApiKeyService apiKeyService) {
        this.clientRepository = clientRepository;
        this.apiKeyService = apiKeyService;
    }

    public CreateClientResponse createClient(final CreateClientRequest request) {
        ClientEntity client = ClientEntity.builder()
            .name(request.name())
            .status(EntityStatus.ACTIVE)
            .build();
        clientRepository.save(client);

        ApiKeyCreationResult keyResult = apiKeyService.generateAndPersist(client.getId(), request.keyLabel());
        return new CreateClientResponse(client.getId(), keyResult.apiKeyId(), keyResult.rawKey());
    }
}
