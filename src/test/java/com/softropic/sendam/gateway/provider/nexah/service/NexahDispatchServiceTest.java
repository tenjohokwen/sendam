package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.provider.nexah.contract.*;
import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NexahDispatchServiceTest {

    @Mock
    private NexahClient nexahClient;
    @Mock
    private SendRequestRepository sendRequestRepository;
    @Mock
    private SendRequestRecipientRepository recipientRepository;
    @Mock
    private NexahProperties nexahProperties;

    @InjectMocks
    private NexahDispatchService nexahDispatchService;

    @Test
    @DisplayName("dispatch: success - moves recipients to SUBMITTED when provider returns IDs")
    void dispatch_success() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("237671234567").build();
        
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of(r1));
        when(nexahProperties.user()).thenReturn("user");
        when(nexahProperties.password()).thenReturn("pass");
        when(nexahProperties.senderid()).thenReturn("sender");
        
        NexahSmsEntry entry = new NexahSmsEntry("Success", "sms-1", "gw-123", "237671234567", 0, "OK", 1, 100);
        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Sent", List.of(entry)));

        nexahDispatchService.dispatch(request);

        assertThat(r1.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        assertThat(r1.getGatewayMessageId()).isEqualTo("gw-123");
        assertThat(request.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        
        verify(recipientRepository).save(r1);
        verify(sendRequestRepository).save(request);
    }

    @Test
    @DisplayName("dispatch: partial success - only matched recipients move to SUBMITTED")
    void dispatch_partialSuccess() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("237671234567").build();
        SendRequestRecipient r2 = SendRequestRecipient.builder().recipient("237671234568").build();
        
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of(r1, r2));
        
        NexahSmsEntry entry1 = new NexahSmsEntry("Success", "sms-1", "gw-123", "237671234567", 0, "OK", 1, 100);
        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Sent", List.of(entry1)));

        nexahDispatchService.dispatch(request);

        assertThat(r1.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        assertThat(r2.getSendStatus()).isEqualTo(SendRequestStatus.ACCEPTED); 
        assertThat(request.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        
        verify(recipientRepository, times(1)).save(any());
        verify(sendRequestRepository).save(request);
    }

    @Test
    @DisplayName("dispatch: failure - rethrows ProviderUnavailableException from client")
    void dispatch_providerUnavailable() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of());
        
        when(nexahClient.sendSms(any())).thenThrow(new ProviderUnavailableException("down"));

        assertThatThrownBy(() -> nexahDispatchService.dispatch(request))
                .isInstanceOf(ProviderUnavailableException.class);
        
        verify(sendRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("dispatch: edge case - nexah returns empty sms list")
    void dispatch_emptyResponse() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").sendStatus(SendRequestStatus.ACCEPTED).build();
        when(recipientRepository.findBySendRequestIdFk(1L)).thenReturn(List.of());
        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Empty", List.of()));

        nexahDispatchService.dispatch(request);

        assertThat(request.getSendStatus()).isEqualTo(SendRequestStatus.ACCEPTED);
        verify(sendRequestRepository, never()).save(any());
    }
}
