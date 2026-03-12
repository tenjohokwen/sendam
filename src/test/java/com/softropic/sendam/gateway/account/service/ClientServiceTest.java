package com.softropic.sendam.gateway.account.service;

import com.softropic.sendam.gateway.account.contract.ClientCreatedEvent;
import com.softropic.sendam.gateway.account.contract.CreateClientRequest;
import com.softropic.sendam.gateway.account.contract.CreateClientResponse;
import com.softropic.sendam.gateway.account.repo.ClientEntity;
import com.softropic.sendam.gateway.account.repo.ClientRepository;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClientService clientService;

    @Test
    @DisplayName("createClient: success - persists client and publishes ClientCreatedEvent")
    void createClient_success() {
        CreateClientRequest request = new CreateClientRequest("New Client", "Primary Key");
        
        when(clientRepository.save(any())).thenAnswer(inv -> {
            ClientEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        CreateClientResponse response = clientService.createClient(request);

        assertThat(response.clientId()).isEqualTo(100L);
        verify(clientRepository).save(any(ClientEntity.class));
        verify(eventPublisher).publishEvent(any(ClientCreatedEvent.class));
        verify(eventPublisher).publishEvent(any(DomainAuditEvent.class));
    }
}
