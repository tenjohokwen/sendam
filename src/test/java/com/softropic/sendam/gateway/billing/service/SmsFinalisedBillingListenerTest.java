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
    private FinalBookingService finalBookingService;

    @InjectMocks
    private SmsFinalisedBillingListener listener;

    @Test
    @DisplayName("onSmsFinalized: delegates to FinalBookingService.book() with all event fields")
    void onSmsFinalized_delegates_book() {
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-123", List.of(), 42L, 2L);

        listener.onSmsFinalized(event);

        verify(finalBookingService).book(100L, "req-123", 2L, 42L);
        verifyNoMoreInteractions(finalBookingService);
    }

    @Test
    @DisplayName("onSmsFinalized: delegates to FinalBookingService.book() when actualSegments is 0")
    void onSmsFinalized_delegates_book_zero_segments() {
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-456", List.of(), 99L, 0L);

        listener.onSmsFinalized(event);

        verify(finalBookingService).book(100L, "req-456", 0L, 99L);
        verifyNoMoreInteractions(finalBookingService);
    }

    @Test
    @DisplayName("onSmsFinalized: delegates to FinalBookingService.book() when reservationId is null")
    void onSmsFinalized_delegates_book_null_reservation() {
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-789", List.of(), null, 5L);

        listener.onSmsFinalized(event);

        verify(finalBookingService).book(100L, "req-789", 5L, null);
        verifyNoMoreInteractions(finalBookingService);
    }
}
