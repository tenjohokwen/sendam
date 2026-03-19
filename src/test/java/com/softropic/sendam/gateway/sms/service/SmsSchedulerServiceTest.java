package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
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

import java.util.ArrayList;
import java.util.List;

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
    @Mock
    private SmsDispatchWorker dispatchWorker;
    @Mock
    private SmsRecoveryWorker recoveryWorker;
    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Spy
    private List<SmsSender> smsSenders = new ArrayList<>();

    @InjectMocks
    private SmsSchedulerService smsSchedulerService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("dispatchScheduledMessages: success - dispatches immediate and due scheduled messages")
    void dispatchScheduledMessages_success() {
        smsSenders.add(smsSender);
        SendRequest req1 = SendRequest.builder().id(1L).sendRequestId("imm-1").build();
        SendRequest req2 = SendRequest.builder().id(2L).sendRequestId("sch-1").build();

        // First call returns 2 requests, second call returns empty list to break the loop
        when(dispatchWorker.grabBatch(any(), anyInt()))
                .thenReturn(List.of(req1, req2))
                .thenReturn(List.of());
        
        SendRequestRecipient r1 = SendRequestRecipient.builder().recipient("671234567").sendStatus(SendRequestStatus.SUBMITTED).build();
        when(dispatchWorker.getRecipients(anyLong())).thenReturn(List.of(r1));

        smsSchedulerService.dispatchScheduledMessages();

        verify(smsSender, times(2)).send(any(), any());
        verify(dispatchWorker, times(2)).saveRecipientStatuses(any());
        verify(dispatchWorker, times(2)).markParentAsSubmitted(any());
    }

    @Test
    @DisplayName("dispatchScheduledMessages: error isolation - failure in one request doesn't block others")
    void dispatchScheduledMessages_errorIsolation() throws ProviderUnavailableException {
        smsSenders.add(smsSender);
        SendRequest r1 = SendRequest.builder().id(1L).sendRequestId("req-1").build();
        SendRequest r2 = SendRequest.builder().id(2L).sendRequestId("req-2").build();

        when(dispatchWorker.grabBatch(any(), anyInt()))
                .thenReturn(List.of(r1, r2))
                .thenReturn(List.of());
        
        doThrow(new RuntimeException("Crash")).when(smsSender).send(eq(r1), any());

        smsSchedulerService.dispatchScheduledMessages();

        verify(smsSender).send(eq(r1), any());
        verify(smsSender).send(eq(r2), any());
        verify(dispatchWorker).handleError(eq(r1), any());
        verify(dispatchWorker).saveRecipientStatuses(any());
        verify(dispatchWorker).markParentAsSubmitted(eq(r2));
    }

    @Test
    @DisplayName("recoverStuckSending: success - recovers requests stuck in SENDING")
    void recoverStuckSending_success() {
        SendRequest req1 = SendRequest.builder().id(1L).sendRequestId("stuck-1").build();
        
        when(recoveryWorker.grabStuckSendingBatch(any(), anyInt()))
                .thenReturn(List.of(req1))
                .thenReturn(List.of());

        smsSchedulerService.recoverStuckSending();

        verify(recoveryWorker).recoverStuckSending(req1);
    }

    @Test
    @DisplayName("recoverStaleSubmitted: success - force-finalizes stale SUBMITTED requests")
    void recoverStaleSubmitted_success() {
        SendRequest req1 = SendRequest.builder().id(1L).sendRequestId("stale-1").build();
        
        when(recoveryWorker.grabStaleSubmittedBatch(any(), anyInt()))
                .thenReturn(List.of(req1))
                .thenReturn(List.of());

        smsSchedulerService.recoverStaleSubmitted();

        verify(recoveryWorker).forceFinalize(req1);
    }

}
