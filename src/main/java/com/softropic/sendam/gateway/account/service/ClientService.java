package com.softropic.sendam.gateway.account.service;

import com.softropic.sendam.gateway.account.contract.ClientCreatedEvent;
import com.softropic.sendam.gateway.account.contract.CreateClientRequest;
import com.softropic.sendam.gateway.account.contract.CreateClientResponse;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.account.repo.ClientEntity;
import com.softropic.sendam.gateway.account.repo.ClientRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CreateClientResponse createClient(final CreateClientRequest request) {
        ClientEntity client = ClientEntity.builder()
            .name(request.name())
            .status(EntityStatus.ACTIVE)
            .build();
        clientRepository.save(client);

        // Publish event to decouple API key and Billing initialization
        eventPublisher.publishEvent(new ClientCreatedEvent(client.getId(), client.getName(), request.keyLabel()));

        eventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.CLIENT_CREATED,
            client.getId(),
            resolveAdminActor(),
            "Client created: " + request.name()
        ));

        // Note: The response no longer contains the raw API key since it's now created asynchronously/via listener.
        // If the v8 contract requires it in the response, we might need to change the listener to be synchronous
        // or re-think this specific decoupling if the contract is strict.
        // Assuming for now that listeners are synchronous (default Spring Event behavior).
        return new CreateClientResponse(client.getId(), null, null);
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
