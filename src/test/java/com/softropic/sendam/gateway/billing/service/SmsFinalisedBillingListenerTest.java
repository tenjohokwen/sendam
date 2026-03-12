package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsFinalisedBillingListenerTest {

    @Mock
    private CreditReservationService creditReservationService;

    @InjectMocks
    private SmsFinalisedBillingListener listener;

    @Test
    @DisplayName("onSmsFinalized: success - debits reservation when actualSegments > 0")
    void onSmsFinalized_debit() {
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-123", List.of(), 42L, 2L);

        listener.onSmsFinalized(event);

        verify(creditReservationService).debit(100L, 42L, 2L);
    }

    @Test
    @DisplayName("onSmsFinalized: success - releases reservation when actualSegments is 0")
    void onSmsFinalized_release() {
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-123", List.of(), 42L, 0L);

        listener.onSmsFinalized(event);

        verify(creditReservationService).release(100L, 42L);
    }
}
