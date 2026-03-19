package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.gateway.sms.contract.ProviderDeliveryReportEvent;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsProviderReportListener {

    private static final Set<SendRequestStatus> TERMINAL_STATUSES = EnumSet.of(
            SendRequestStatus.COMPLETED,
            SendRequestStatus.FAILED,
            SendRequestStatus.FINALIZED,
            SendRequestStatus.FAIL_FINALIZED
    );

    private static final String DELIVRD = "DELIVRD";

    private final SendRequestRecipientRepository recipientRepository;
    private final SendRequestRepository sendRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void onProviderReport(ProviderDeliveryReportEvent event) {
        for (ProviderDeliveryReportEvent.DlrEntry dlr : event.dlrList()) {
            try {
                processSingleEntry(dlr);
            } catch (Exception e) {
                log.error("Failed to process DLR entry for messageId={}", dlr.messageId(), e);
            }
        }
    }

    private void processSingleEntry(ProviderDeliveryReportEvent.DlrEntry dlr) {
        Optional<SendRequestRecipient> recipientOpt = recipientRepository.findByGatewayMessageId(dlr.messageId());
        if (recipientOpt.isEmpty()) {
            log.warn("DLR for unknown gatewayMessageId={}.", dlr.messageId());
            return;
        }

        SendRequestRecipient recipient = recipientOpt.get();

        if (TERMINAL_STATUSES.contains(recipient.getSendStatus())) {
            return;
        }

        int segmentsConsumed = parseSegmentsConsumed(dlr.totalSmsUnit());
        recipient.setSegmentsConsumed(segmentsConsumed);
        
        SendRequestStatus recipientStatus = DELIVRD.equals(dlr.status())
                ? SendRequestStatus.COMPLETED
                : SendRequestStatus.FAILED;
        recipient.setSendStatus(recipientStatus);
        recipientRepository.save(recipient);

        SendRequest parent = sendRequestRepository.findById(recipient.getSendRequestIdFk())
                .orElseThrow(() -> new ResourceNotFoundException("Parent SendRequest not found", "send_request"));

        List<SendRequestRecipient> allRecipients = recipientRepository.findBySendRequestIdFk(parent.getId());
        finalizeParentIfAllTerminal(parent, allRecipients);
    }

    private void finalizeParentIfAllTerminal(SendRequest parent, List<SendRequestRecipient> recipients) {
        boolean allTerminal = recipients.stream()
                .allMatch(r -> TERMINAL_STATUSES.contains(r.getSendStatus()));

        if (!allTerminal) {
            return;
        }

        boolean anyFailed = recipients.stream()
                .anyMatch(r -> r.getSendStatus() == SendRequestStatus.FAILED
                        || r.getSendStatus() == SendRequestStatus.FAIL_FINALIZED);
        SendRequestStatus parentStatus = anyFailed
                ? SendRequestStatus.FAIL_FINALIZED
                : SendRequestStatus.FINALIZED;

        int totalActualSegments = recipients.stream()
                .mapToInt(r -> r.getSegmentsConsumed() != null ? r.getSegmentsConsumed() : 0)
                .sum();

        parent.setSendStatus(parentStatus);
        parent.setFinalizedAt(Instant.now());
        sendRequestRepository.save(parent);

        log.info("SendRequest {} finalized as {} — totalSegments={}",
                parent.getSendRequestId(), parentStatus, totalActualSegments);

        List<SmsFinalisedEvent.RecipientSummary> summaries = recipients.stream()
                .map(r -> new SmsFinalisedEvent.RecipientSummary(
                        r.getRecipient(),
                        r.getGatewayMessageId(),
                        r.getSendStatus()
                ))
                .toList();
        
        // This event now triggers BOTH Billing and Webhook listeners
        eventPublisher.publishEvent(new SmsFinalisedEvent(
            parent.getClientId(), 
            parent.getSendRequestId(), 
            summaries,
            parent.getReservationId(),
            totalActualSegments
        ));
    }

    private int parseSegmentsConsumed(String value) {
        if (value == null || value.isBlank()) return 1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
