package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.provider.nexah.contract.*;
import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;

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
    private NexahProperties nexahProperties;

    @InjectMocks
    private NexahDispatchService nexahDispatchService;

    @Test
    @DisplayName("send: success - moves recipients to SUBMITTED when provider returns IDs")
    void send_success() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("237671234567").build();
        
        lenient().when(nexahProperties.user()).thenReturn("user");
        lenient().when(nexahProperties.password()).thenReturn("pass");
        lenient().when(nexahProperties.senderid()).thenReturn("sender");
        
        NexahSmsEntry entry = new NexahSmsEntry("Success", "sms-1", "gw-123", "237671234567", 0, "OK", 1, 100);
        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Sent", List.of(entry)));

        nexahDispatchService.send(request, List.of(r1));

        assertThat(r1.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        assertThat(r1.getGatewayMessageId()).isEqualTo("gw-123");
    }

    @Test
    @DisplayName("send: partial success - only matched recipients move to SUBMITTED")
    void send_partialSuccess() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("237671234567").build();
        SendRequestRecipient r2 = SendRequestRecipient.builder().recipient("237671234568").build();
        
        lenient().when(nexahProperties.user()).thenReturn("user");
        lenient().when(nexahProperties.password()).thenReturn("pass");
        lenient().when(nexahProperties.senderid()).thenReturn("sender");

        NexahSmsEntry entry1 = new NexahSmsEntry("Success", "sms-1", "gw-123", "237671234567", 0, "OK", 1, 100);
        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Sent", List.of(entry1)));

        nexahDispatchService.send(request, List.of(r1, r2));

        assertThat(r1.getSendStatus()).isEqualTo(SendRequestStatus.SUBMITTED);
        assertThat(r2.getSendStatus()).isEqualTo(SendRequestStatus.ACCEPTED); 
    }

    @Test
    @DisplayName("send: failure - rethrows ProviderUnavailableException from client")
    void send_providerUnavailable() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").build();
        
        lenient().when(nexahProperties.user()).thenReturn("user");
        lenient().when(nexahProperties.password()).thenReturn("pass");
        lenient().when(nexahProperties.senderid()).thenReturn("sender");

        when(nexahClient.sendSms(any())).thenThrow(new ProviderUnavailableException("down"));

        assertThatThrownBy(() -> nexahDispatchService.send(request, List.of()))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    @DisplayName("send: edge case - nexah returns empty sms list")
    void send_emptyResponse() {
        SendRequest request = SendRequest.builder().id(1L).sendRequestId("req-123").message("test").sendStatus(SendRequestStatus.ACCEPTED).build();
        
        lenient().when(nexahProperties.user()).thenReturn("user");
        lenient().when(nexahProperties.password()).thenReturn("pass");
        lenient().when(nexahProperties.senderid()).thenReturn("sender");

        when(nexahClient.sendSms(any())).thenReturn(new NexahSendResponse(1, "OK", "Empty", List.of()));

        nexahDispatchService.send(request, List.of());

        assertThat(request.getSendStatus()).isEqualTo(SendRequestStatus.ACCEPTED);
    }
}
