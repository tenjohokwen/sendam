package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.provider.nexah.service.DrCallbackService;
import com.softropic.sendam.gateway.provider.nexah.service.NexahDispatchService;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsSchedulerServiceTest {

    @Mock
    private SendRequestRepository sendRequestRepository;
    @Mock
    private NexahDispatchService nexahDispatchService;
    @Mock
    private DrCallbackService drCallbackService;

    @InjectMocks
    private SmsSchedulerService smsSchedulerService;

    @Test
    @DisplayName("dispatchScheduledMessages: success - dispatches immediate and due scheduled messages")
    void dispatchScheduledMessages_success() {
        SendRequest immediate = SendRequest.builder().id(1L).sendRequestId("imm-1").build();
        SendRequest scheduled = SendRequest.builder().id(2L).sendRequestId("sch-1").build();

        when(sendRequestRepository.findDueScheduledRequests(any())).thenReturn(List.of(scheduled));
        when(sendRequestRepository.findPendingImmediateRequests()).thenReturn(List.of(immediate));

        smsSchedulerService.dispatchScheduledMessages();

        verify(nexahDispatchService).dispatch(immediate);
        verify(nexahDispatchService).dispatch(scheduled);
    }

    @Test
    @DisplayName("dispatchScheduledMessages: error isolation - failure in one request doesn't block others")
    void dispatchScheduledMessages_errorIsolation() {
        SendRequest r1 = SendRequest.builder().id(1L).sendRequestId("req-1").build();
        SendRequest r2 = SendRequest.builder().id(2L).sendRequestId("req-2").build();

        when(sendRequestRepository.findPendingImmediateRequests()).thenReturn(List.of(r1, r2));
        doThrow(new RuntimeException("Crash")).when(nexahDispatchService).dispatch(r1);

        smsSchedulerService.dispatchScheduledMessages();

        verify(nexahDispatchService).dispatch(r1);
        verify(nexahDispatchService).dispatch(r2);
    }

    @Test
    @DisplayName("recoverStaleSms: success - finalizes stale requests")
    void recoverStaleSms_success() {
        SendRequest stale = SendRequest.builder().id(10L).sendRequestId("stale-1").build();
        when(sendRequestRepository.findStaleSubmittedRequests(any())).thenReturn(List.of(stale));

        smsSchedulerService.recoverStaleSms();

        verify(drCallbackService).forceFinalizeStaleSms(10L);
    }
}
