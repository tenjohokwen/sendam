package com.softropic.sendam.gateway.account.service;

import com.softropic.sendam.gateway.account.repo.ClientEntity;
import com.softropic.sendam.gateway.account.repo.ClientRepository;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientFreezeServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private SendRequestRepository sendRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClientFreezeService clientFreezeService;

    @Test
    @DisplayName("freeze: sets frozen=true, suspends SMS, publishes CLIENT_ACCOUNT_FROZEN audit event")
    void freeze_setsFieldsAndSuspendsSms() {
        Long clientId = 42L;
        String reason = "Suspicious activity";

        ClientEntity client = ClientEntity.builder().name("test-client").build();
        when(clientRepository.findByIdForUpdate(clientId)).thenReturn(Optional.of(client));
        when(sendRequestRepository.suspendScheduledForClient(clientId)).thenReturn(3);
        when(clientRepository.save(any())).thenReturn(client);

        clientFreezeService.freeze(clientId, reason);

        assertThat(client.isFrozen()).isTrue();
        assertThat(client.getFrozenAt()).isNotNull();
        assertThat(client.getFreezeReason()).isEqualTo(reason);
        assertThat(client.getFreezeResolvedAt()).isNull();
        assertThat(client.getFreezeResolution()).isNull();

        verify(sendRequestRepository).suspendScheduledForClient(clientId);

        ArgumentCaptor<DomainAuditEvent> eventCaptor = ArgumentCaptor.forClass(DomainAuditEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DomainAuditEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventType()).isEqualTo(AuditEventType.CLIENT_ACCOUNT_FROZEN);
        assertThat(publishedEvent.clientId()).isEqualTo(clientId);
        assertThat(publishedEvent.detail()).contains("suspendedSms=3");
    }

    @Test
    @DisplayName("unfreeze: sets frozen=false, resumes SMS, publishes CLIENT_ACCOUNT_UNFROZEN audit event")
    void unfreeze_setsFieldsAndResumesSms() {
        Long clientId = 42L;
        String resolution = "Issue resolved by admin";

        ClientEntity client = ClientEntity.builder().name("test-client").build();
        client.setFrozen(true);
        when(clientRepository.findByIdForUpdate(clientId)).thenReturn(Optional.of(client));
        when(sendRequestRepository.resumeScheduledForClient(clientId)).thenReturn(2);
        when(clientRepository.save(any())).thenReturn(client);

        clientFreezeService.unfreeze(clientId, resolution);

        assertThat(client.isFrozen()).isFalse();
        assertThat(client.getFreezeResolvedAt()).isNotNull();
        assertThat(client.getFreezeResolution()).isEqualTo(resolution);

        verify(sendRequestRepository).resumeScheduledForClient(clientId);

        ArgumentCaptor<DomainAuditEvent> eventCaptor = ArgumentCaptor.forClass(DomainAuditEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DomainAuditEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventType()).isEqualTo(AuditEventType.CLIENT_ACCOUNT_UNFROZEN);
        assertThat(publishedEvent.clientId()).isEqualTo(clientId);
        assertThat(publishedEvent.detail()).contains("resumedSms=2");
    }

    @Test
    @DisplayName("isFrozen: returns true when client is frozen")
    void isFrozen_returnsTrue_whenFrozen() {
        Long clientId = 42L;
        ClientEntity client = ClientEntity.builder().name("test-client").build();
        client.setFrozen(true);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        boolean result = clientFreezeService.isFrozen(clientId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isFrozen: returns false when client is not frozen")
    void isFrozen_returnsFalse_whenNotFrozen() {
        Long clientId = 42L;
        ClientEntity client = ClientEntity.builder().name("test-client").build();
        // frozen defaults to false
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        boolean result = clientFreezeService.isFrozen(clientId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("freeze: throws ResourceNotFoundException when client does not exist")
    void freeze_throwsResourceNotFound_whenClientMissing() {
        Long clientId = 99L;
        when(clientRepository.findByIdForUpdate(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientFreezeService.freeze(clientId, "reason"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(sendRequestRepository, never()).suspendScheduledForClient(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
