package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrAck;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrEntry;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrPayload;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrResponse;
import com.softropic.sendam.gateway.sms.contract.ProviderDeliveryReportEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses inbound delivery report callbacks from Nexah and publishes domain events.
 * This service is now decoupled from the core SMS and Billing logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrCallbackService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Processes a DR callback payload from Nexah.
     *
     * @param payload the inbound DR payload containing a list of delivery report entries
     * @return NexahDrResponse with per-entry acknowledgement status (1=success, 0=retry)
     */
    public NexahDrResponse processDr(NexahDrPayload payload) {
        if (payload.dlrList() == null || payload.dlrList().isEmpty()) {
            return new NexahDrResponse(new ArrayList<>());
        }

        List<ProviderDeliveryReportEvent.DlrEntry> eventEntries = payload.dlrList().stream()
                .map(dlr -> new ProviderDeliveryReportEvent.DlrEntry(
                        dlr.messageId(),
                        dlr.status(),
                        dlr.totalSmsUnit(),
                        dlr.mobileNo()
                ))
                .toList();

        // Publish event for the SMS module to handle
        eventPublisher.publishEvent(new ProviderDeliveryReportEvent(eventEntries));

        // Build positive acknowledgements for Nexah. 
        // Note: In EDA, we acknowledge provider BEFORE we know if processing succeeded.
        // If processing fails, the SMS module must handle retries or stale-job recovery.
        List<NexahDrAck> acks = payload.dlrList().stream()
                .map(dlr -> buildAck(dlr, 1))
                .toList();

        return new NexahDrResponse(acks);
    }

    private NexahDrAck buildAck(NexahDrEntry dlr, int status) {
        return new NexahDrAck(
                dlr.responseCode() != null ? tryParseInt(dlr.responseCode(), 0) : 0,
                dlr.responseDescription(),
                dlr.messageId(),
                dlr.mobileNo(),
                status,
                dlr.submitTime(),
                dlr.sentTime(),
                dlr.deliveryTime()
        );
    }

    private int tryParseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
