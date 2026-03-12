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

    private final CreditReservationService creditReservationService;

    @EventListener
    @Transactional
    public void onSmsFinalized(SmsFinalisedEvent event) {
        log.info("Finalizing billing for sendRequestId={}, actualSegments={}", 
                event.sendRequestId(), event.actualSegments());
        
        if (event.reservationId() == null) {
            log.warn("No reservationId found for sendRequestId={} - skipping billing finalization", event.sendRequestId());
            return;
        }

        if (event.actualSegments() > 0) {
            creditReservationService.debit(event.clientId(), event.reservationId(), event.actualSegments());
        } else {
            // If zero segments (e.g. all failed before dispatch), release the whole reservation
            creditReservationService.release(event.clientId(), event.reservationId());
        }
    }
}
