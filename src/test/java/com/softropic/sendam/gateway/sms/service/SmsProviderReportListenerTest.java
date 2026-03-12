package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.ProviderDeliveryReportEvent;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsProviderReportListenerTest {

    @Mock
    private SendRequestRecipientRepository recipientRepository;
    @Mock
    private SendRequestRepository sendRequestRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SmsProviderReportListener listener;

    @Test
    @DisplayName("onProviderReport: success - terminal DLR finalizes parent and publishes event")
    void onProviderReport_success() {
        SendRequestRecipient recipient = SendRequestRecipient.builder()
                .id(10L)
                .sendRequestIdFk(1L)
                .gatewayMessageId("gw-123")
                .sendStatus(SendRequestStatus.SUBMITTED)
                .build();
        
        SendRequest parent = SendRequest.builder()
                .id(1L)
                .clientId(100L)
                .sendRequestId("req-123")
                .reservationId(42L)
                .reservedCredits(2L)
                .build();

        when(recipientRepository.findByGatewayMessageId("gw-123")).thenReturn(Optional.of(recipient));
        when(sendRequestRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of(recipient));

        ProviderDeliveryReportEvent.DlrEntry entry = new ProviderDeliveryReportEvent.DlrEntry("gw-123", "DELIVRD", "2", "237671234567");
        ProviderDeliveryReportEvent event = new ProviderDeliveryReportEvent(List.of(entry));

        listener.onProviderReport(event);

        assertThat(recipient.getSendStatus()).isEqualTo(SendRequestStatus.COMPLETED);
        assertThat(parent.getSendStatus()).isEqualTo(SendRequestStatus.FINALIZED);
        
        verify(eventPublisher).publishEvent(any(SmsFinalisedEvent.class));
        verify(sendRequestRepository).save(parent);
        verify(recipientRepository).save(recipient);
    }
}
