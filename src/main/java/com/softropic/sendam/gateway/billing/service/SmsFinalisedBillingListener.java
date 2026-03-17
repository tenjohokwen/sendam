package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsFinalisedBillingListener {

    private final FinalBookingService finalBookingService;

    @EventListener
    @Transactional
    public void onSmsFinalized(SmsFinalisedEvent event) {
        log.info("Finalizing billing for sendRequestId={}, actualSegments={}",
                event.sendRequestId(), event.actualSegments());
        finalBookingService.book(
                event.clientId(),
                event.sendRequestId(),
                event.actualSegments(),
                event.reservationId());
    }
}
