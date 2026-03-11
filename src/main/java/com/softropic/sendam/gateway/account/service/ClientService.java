package com.softropic.sendam.gateway.account.service;

import com.softropic.sendam.gateway.auth.contract.ApiKeyCreationResult;
import com.softropic.sendam.gateway.account.contract.CreateClientRequest;
import com.softropic.sendam.gateway.account.contract.CreateClientResponse;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.account.repo.ClientEntity;
import com.softropic.sendam.gateway.account.repo.ClientRepository;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.gateway.auth.service.ApiKeyService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ApiKeyService apiKeyService;
    private final ClientCreditBalanceRepository clientCreditBalanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ClientService(final ClientRepository clientRepository,
                         final ApiKeyService apiKeyService,
                         final ClientCreditBalanceRepository clientCreditBalanceRepository,
                         final ApplicationEventPublisher eventPublisher) {
        this.clientRepository = clientRepository;
        this.apiKeyService = apiKeyService;
        this.clientCreditBalanceRepository = clientCreditBalanceRepository;
        this.eventPublisher = eventPublisher;
    }

    public CreateClientResponse createClient(final CreateClientRequest request) {
        ClientEntity client = ClientEntity.builder()
            .name(request.name())
            .status(EntityStatus.ACTIVE)
            .build();
        clientRepository.save(client);

        // Create balance lock row atomically with client creation — ensures no ResourceNotFoundException on first balance query
        ClientCreditBalance balanceRow = ClientCreditBalance.builder()
                .clientId(client.getId())
                .balance(0L)
                .status(EntityStatus.ACTIVE)
                .build();
        clientCreditBalanceRepository.save(balanceRow);

        ApiKeyCreationResult keyResult = apiKeyService.generateAndPersist(client.getId(), request.keyLabel());

        eventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.CLIENT_CREATED,
            client.getId(),
            resolveAdminActor(),
            "Client created: " + request.name()
        ));

        return new CreateClientResponse(client.getId(), keyResult.apiKeyId(), keyResult.rawKey());
    }

    private String resolveAdminActor() {
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
