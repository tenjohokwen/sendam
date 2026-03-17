package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.springframework.context.ApplicationEventPublisher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsSchedulerServiceTest {

    @Mock
    private SendRequestRepository sendRequestRepository;
    @Mock
    private SendRequestRecipientRepository recipientRepository;
    @Mock
    private SmsSender smsSender;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private List<SmsSender> smsSenders = new ArrayList<>();

    @InjectMocks
    private SmsSchedulerService smsSchedulerService;

    @Test
    @DisplayName("dispatchScheduledMessages: success - dispatches immediate and due scheduled messages")
    void dispatchScheduledMessages_success() {
        smsSenders.add(smsSender);
        SendRequest immediate = SendRequest.builder().id(1L).sendRequestId("imm-1").build();
        SendRequest scheduled = SendRequest.builder().id(2L).sendRequestId("sch-1").build();

        when(sendRequestRepository.findDueScheduledRequests(any())).thenReturn(List.of(scheduled));
        when(sendRequestRepository.findPendingImmediateRequests()).thenReturn(List.of(immediate));
        
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("671234567").sendStatus(SendRequestStatus.SUBMITTED).build();
        when(recipientRepository.findBySendRequestIdFk(anyLong())).thenReturn(List.of(r1));

        smsSchedulerService.dispatchScheduledMessages();

        verify(smsSender, times(2)).send(any(), any());
        verify(sendRequestRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("dispatchScheduledMessages: error isolation - failure in one request doesn't block others")
    void dispatchScheduledMessages_errorIsolation() throws ProviderUnavailableException {
        smsSenders.add(smsSender);
        SendRequest r1 = SendRequest.builder().id(1L).sendRequestId("req-1").build();
        SendRequest r2 = SendRequest.builder().id(2L).sendRequestId("req-2").build();

        when(sendRequestRepository.findPendingImmediateRequests()).thenReturn(List.of(r1, r2));
        doThrow(new RuntimeException("Crash")).when(smsSender).send(eq(r1), any());

        smsSchedulerService.dispatchScheduledMessages();

        verify(smsSender).send(eq(r1), any());
        verify(smsSender).send(eq(r2), any());
    }

    @Test
    @DisplayName("recoverStaleSms: success - finalizes stale requests")
    void recoverStaleSms_success() {
        SendRequest stale = SendRequest.builder().id(10L).sendRequestId("stale-1").segmentCount(1).build();
        when(sendRequestRepository.findStaleSubmittedRequests(any())).thenReturn(List.of(stale));
        
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("671234567").sendStatus(SendRequestStatus.SUBMITTED).build();
        when(recipientRepository.findBySendRequestIdFk(10L)).thenReturn(List.of(r1));

        smsSchedulerService.recoverStaleSms();

        assertThat(stale.getSendStatus()).isEqualTo(SendRequestStatus.FAIL_FINALIZED);
        verify(sendRequestRepository).save(stale);
        verify(recipientRepository).save(r1);
        verify(eventPublisher).publishEvent(any(SmsFinalisedEvent.class));
    }
}
