package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.billing.repo.PlatformFreezeState;
import com.softropic.sendam.gateway.billing.repo.PlatformFreezeStateRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformFreezeServiceTest {

    @Mock
    private PlatformFreezeStateRepository freezeStateRepository;

    @Mock
    private SendRequestRepository sendRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PlatformFreezeService platformFreezeService;

    @Test
    @DisplayName("freeze: sets frozen=true, records shortfall, suspends all SMS, publishes PLATFORM_FROZEN audit event")
    void freeze_setsStateAndSuspendsAll() {
        String reason = "Low balance trigger";
        Long shortfall = 5000L;

        PlatformFreezeState state = PlatformFreezeState.builder().build();
        when(freezeStateRepository.findForUpdate()).thenReturn(Optional.of(state));
        when(sendRequestRepository.suspendAllScheduled()).thenReturn(5);
        when(freezeStateRepository.save(any())).thenReturn(state);

        platformFreezeService.freeze(reason, shortfall);

        assertThat(state.isFrozen()).isTrue();
        assertThat(state.getFrozenAt()).isNotNull();
        assertThat(state.getFreezeReason()).isEqualTo(reason);
        assertThat(state.getShortfallAmount()).isEqualTo(shortfall);
        assertThat(state.getFreezeResolvedAt()).isNull();
        assertThat(state.getFreezeResolution()).isNull();

        verify(sendRequestRepository).suspendAllScheduled();

        ArgumentCaptor<DomainAuditEvent> eventCaptor = ArgumentCaptor.forClass(DomainAuditEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DomainAuditEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventType()).isEqualTo(AuditEventType.PLATFORM_FROZEN);
        assertThat(publishedEvent.clientId()).isNull();
        assertThat(publishedEvent.detail()).contains("suspendedSms=5");
        assertThat(publishedEvent.detail()).contains("shortfall=5000");
    }

    @Test
    @DisplayName("freeze: works with null shortfall (admin-initiated freeze)")
    void freeze_setsStateAndSuspendsAll_nullShortfall() {
        String reason = "Admin manual freeze";

        PlatformFreezeState state = PlatformFreezeState.builder().build();
        when(freezeStateRepository.findForUpdate()).thenReturn(Optional.of(state));
        when(sendRequestRepository.suspendAllScheduled()).thenReturn(0);
        when(freezeStateRepository.save(any())).thenReturn(state);

        platformFreezeService.freeze(reason, null);

        assertThat(state.isFrozen()).isTrue();
        assertThat(state.getShortfallAmount()).isNull();
    }

    @Test
    @DisplayName("unfreeze: sets frozen=false, resumes all SMS, publishes PLATFORM_UNFROZEN audit event")
    void unfreeze_setsStateAndResumesAll() {
        String resolution = "Topped up platform balance";

        PlatformFreezeState state = PlatformFreezeState.builder().build();
        state.setFrozen(true);
        when(freezeStateRepository.findForUpdate()).thenReturn(Optional.of(state));
        when(sendRequestRepository.resumeAllScheduled()).thenReturn(3);
        when(freezeStateRepository.save(any())).thenReturn(state);

        platformFreezeService.unfreeze(resolution);

        assertThat(state.isFrozen()).isFalse();
        assertThat(state.getFreezeResolvedAt()).isNotNull();
        assertThat(state.getFreezeResolution()).isEqualTo(resolution);

        verify(sendRequestRepository).resumeAllScheduled();

        ArgumentCaptor<DomainAuditEvent> eventCaptor = ArgumentCaptor.forClass(DomainAuditEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DomainAuditEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventType()).isEqualTo(AuditEventType.PLATFORM_UNFROZEN);
        assertThat(publishedEvent.clientId()).isNull();
        assertThat(publishedEvent.detail()).contains("resumedSms=3");
    }

    @Test
    @DisplayName("isFrozen: returns current frozen state from non-locking read")
    void isFrozen_returnsCurrentState() {
        PlatformFreezeState state = PlatformFreezeState.builder().build();
        state.setFrozen(true);
        when(freezeStateRepository.findState()).thenReturn(Optional.of(state));

        boolean result = platformFreezeService.isFrozen();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("freeze: throws ResourceNotFoundException when platform state not initialized")
    void freeze_throwsResourceNotFound_whenStateNotInitialized() {
        when(freezeStateRepository.findForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformFreezeService.freeze("reason", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not initialized");

        verify(sendRequestRepository, never()).suspendAllScheduled();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
