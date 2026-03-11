package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.provider.nexah.contract.*;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.*;
import com.softropic.sendam.gateway.billing.service.CreditReservationService;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrCallbackServiceTest {

    @Mock
    private SendRequestRecipientRepository recipientRepository;
    @Mock
    private SendRequestRepository sendRequestRepository;
    @Mock
    private CreditReservationService creditReservationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DrCallbackService drCallbackService;

    @Test
    @DisplayName("processDr: success - terminal DLR finalizes parent and debits credits")
    void processDr_success_terminal() {
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

        NexahDrEntry entry = new NexahDrEntry("1", "Success", "237670000001", "gw-123", "2", "t1", "t2", "t3", "DELIVRD", "traff");
        NexahDrPayload payload = new NexahDrPayload(List.of(entry));

        NexahDrResponse response = drCallbackService.processDr(payload);

        assertThat(response.dlrList()).hasSize(1);
        assertThat(response.dlrList().get(0).status()).isEqualTo(1);
        assertThat(recipient.getSendStatus()).isEqualTo(SendRequestStatus.COMPLETED);
        assertThat(parent.getSendStatus()).isEqualTo(SendRequestStatus.FINALIZED);
        
        verify(creditReservationService).debit(eq(100L), eq(42L), eq(2L));
        verify(eventPublisher).publishEvent(any(SmsFinalisedEvent.class));
    }

    @Test
    @DisplayName("processDr: idempotency - already terminal recipient acknowledged without re-processing")
    void processDr_idempotency() {
        SendRequestRecipient recipient = SendRequestRecipient.builder()
                .gatewayMessageId("gw-123")
                .sendStatus(SendRequestStatus.COMPLETED)
                .build();

        when(recipientRepository.findByGatewayMessageId("gw-123")).thenReturn(Optional.of(recipient));

        NexahDrEntry entry = new NexahDrEntry("1", "Success", "237670000001", "gw-123", "2", "t1", "t2", "t3", "DELIVRD", "traff");
        NexahDrPayload payload = new NexahDrPayload(List.of(entry));

        NexahDrResponse response = drCallbackService.processDr(payload);

        assertThat(response.dlrList().get(0).status()).isEqualTo(1);
        verify(recipientRepository, never()).save(any());
        verify(creditReservationService, never()).debit(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("forceFinalizeStaleSms: success - moves SUBMITTED recipients to FAILED and finalizes")
    void forceFinalizeStaleSms_success() {
        SendRequest parent = SendRequest.builder()
                .id(1L)
                .clientId(100L)
                .sendRequestId("req-123")
                .reservationId(42L)
                .reservedCredits(1L)
                .segmentCount(1)
                .build();
        
        SendRequestRecipient recipient = SendRequestRecipient.builder()
                .sendRequestIdFk(1L)
                .sendStatus(SendRequestStatus.SUBMITTED)
                .build();

        when(sendRequestRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of(recipient));

        drCallbackService.forceFinalizeStaleSms(1L);

        assertThat(recipient.getSendStatus()).isEqualTo(SendRequestStatus.FAILED);
        assertThat(parent.getSendStatus()).isEqualTo(SendRequestStatus.FAIL_FINALIZED);
        
        verify(creditReservationService).debit(eq(100L), eq(42L), eq(1L));
    }
}
