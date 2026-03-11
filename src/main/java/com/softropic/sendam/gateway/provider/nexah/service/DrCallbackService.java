package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrAck;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrEntry;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrPayload;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrResponse;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import com.softropic.sendam.gateway.billing.service.CreditReservationService;
import com.softropic.sendam.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Processes inbound delivery report callbacks from Nexah.
 *
 * <p>Each DR entry advances a recipient from SUBMITTED to COMPLETED (DELIVRD)
 * or FAILED. When all recipients for a parent SendRequest reach a terminal state,
 * the parent is finalized and billing is settled via CreditReservationService.debit().
 *
 * <p>Idempotency: duplicate DRs for already-terminal recipients are acknowledged
 * with status=1 without calling debit() again.
 *
 * <p>Error isolation: each entry is processed independently. A failure on one entry
 * returns status=0 (Nexah retries that entry) without aborting the rest of the batch.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DrCallbackService {

    private static final Set<SendRequestStatus> TERMINAL_STATUSES = EnumSet.of(
            SendRequestStatus.COMPLETED,
            SendRequestStatus.FAILED,
            SendRequestStatus.FINALIZED,
            SendRequestStatus.FAIL_FINALIZED
    );

    private static final String DELIVRD = "DELIVRD";

    private final SendRequestRecipientRepository recipientRepository;
    private final SendRequestRepository sendRequestRepository;
    private final CreditReservationService creditReservationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Processes a DR callback payload from Nexah.
     *
     * @param payload the inbound DR payload containing a list of delivery report entries
     * @return NexahDrResponse with per-entry acknowledgement status (1=success, 0=retry)
     */
    public NexahDrResponse processDr(NexahDrPayload payload) {
        List<NexahDrAck> results = new ArrayList<>();

        if (payload.dlrList() == null || payload.dlrList().isEmpty()) {
            return new NexahDrResponse(results);
        }

        for (NexahDrEntry dlr : payload.dlrList()) {
            try {
                NexahDrAck ack = processSingleEntry(dlr);
                results.add(ack);
            } catch (Exception e) {
                log.error("Unexpected error processing DR for messageId={}. Nexah will retry.",
                        dlr.messageId(), e);
                // status=0 tells Nexah to retry this entry
                results.add(buildAck(dlr, 0));
            }
        }

        return new NexahDrResponse(results);
    }

    /**
     * Force-finalizes a SendRequest that has been stuck in SUBMITTED state for 24+ hours.
     * Sets any remaining SUBMITTED recipients to FAILED with estimated segment count,
     * then runs the same finalization logic as normal DR processing.
     *
     * @param sendRequestId the database ID of the stale send request
     */
    public void forceFinalizeStaleSms(Long sendRequestId) {
        SendRequest parent = sendRequestRepository.findById(sendRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SendRequest not found: " + sendRequestId, "send_request"));

        log.warn("Force-finalizing stale SendRequest {} (sendRequestId={})",
                sendRequestId, parent.getSendRequestId());

        List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(sendRequestId);

        for (SendRequestRecipient recipient : recipients) {
            if (recipient.getSendStatus() == SendRequestStatus.SUBMITTED) {
                int estimatedSegments = Math.max(1, parent.getSegmentCount());
                recipient.setSegmentsConsumed(estimatedSegments);
                recipient.setSendStatus(SendRequestStatus.FAILED);
                recipientRepository.save(recipient);
            }
        }

        // Re-load recipients to reflect the updates above
        List<SendRequestRecipient> updatedRecipients = recipientRepository.findBySendRequestIdFk(sendRequestId);
        finalizeParentIfAllTerminal(parent, updatedRecipients);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private NexahDrAck processSingleEntry(NexahDrEntry dlr) {
        // Step a: Find recipient by gatewayMessageId
        Optional<SendRequestRecipient> recipientOpt = recipientRepository.findByGatewayMessageId(dlr.messageId());
        if (recipientOpt.isEmpty()) {
            log.warn("DR for unknown gatewayMessageId={}. Returning status=0 so Nexah retries.", dlr.messageId());
            return buildAck(dlr, 0);
        }

        SendRequestRecipient recipient = recipientOpt.get();

        // Step b: Idempotency guard — already terminal
        if (TERMINAL_STATUSES.contains(recipient.getSendStatus())) {
            log.debug("Duplicate DR for gatewayMessageId={} — recipient already {}. Acknowledging without re-processing.",
                    dlr.messageId(), recipient.getSendStatus());
            return buildAck(dlr, 1);
        }

        // Step c: Parse segmentsConsumed
        int segmentsConsumed = parseSegmentsConsumed(dlr);

        // Step d: Update recipient state
        recipient.setSegmentsConsumed(segmentsConsumed);
        SendRequestStatus recipientStatus = DELIVRD.equals(dlr.status())
                ? SendRequestStatus.COMPLETED
                : SendRequestStatus.FAILED;
        recipient.setSendStatus(recipientStatus);
        recipientRepository.save(recipient);

        // Step e: Check if all recipients are terminal — finalize parent if so
        SendRequest parent = sendRequestRepository.findById(recipient.getSendRequestIdFk())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent SendRequest not found: " + recipient.getSendRequestIdFk(), "send_request"));

        List<SendRequestRecipient> allRecipients = recipientRepository.findBySendRequestIdFk(parent.getId());
        finalizeParentIfAllTerminal(parent, allRecipients);

        // Step f: Acknowledge success
        return buildAck(dlr, 1);
    }

    private void finalizeParentIfAllTerminal(SendRequest parent, List<SendRequestRecipient> recipients) {
        boolean allTerminal = recipients.stream()
                .allMatch(r -> TERMINAL_STATUSES.contains(r.getSendStatus()));

        if (!allTerminal) {
            return;
        }

        // Determine parent terminal status: FAIL_FINALIZED if any recipient failed
        boolean anyFailed = recipients.stream()
                .anyMatch(r -> r.getSendStatus() == SendRequestStatus.FAILED
                        || r.getSendStatus() == SendRequestStatus.FAIL_FINALIZED);
        SendRequestStatus parentStatus = anyFailed
                ? SendRequestStatus.FAIL_FINALIZED
                : SendRequestStatus.FINALIZED;

        // Compute total actual segments across all recipients
        int totalActualSegments = recipients.stream()
                .mapToInt(r -> r.getSegmentsConsumed() != null ? r.getSegmentsConsumed() : 0)
                .sum();

        // Conservative fallback: if zero (should not happen), use reserved credits
        if (totalActualSegments <= 0) {
            log.warn("SendRequest {} totalActualSegments is 0 — falling back to reservedCredits {}",
                    parent.getId(), parent.getReservedCredits());
            totalActualSegments = (int) Math.min(parent.getReservedCredits(), Integer.MAX_VALUE);
        }

        // Cap to reserved credits to prevent IllegalArgumentException from debit()
        int debitable = (int) Math.min(totalActualSegments, parent.getReservedCredits());
        if (totalActualSegments > parent.getReservedCredits()) {
            log.warn("SendRequest {} actual segments {} exceeds reservation {} — capping debit to {}",
                    parent.getId(), totalActualSegments, parent.getReservedCredits(), debitable);
        }

        creditReservationService.debit(parent.getClientId(), parent.getReservationId(), debitable);

        parent.setSendStatus(parentStatus);
        parent.setFinalizedAt(Instant.now());
        sendRequestRepository.save(parent);

        log.info("SendRequest {} finalized as {} — debited {} segments (reserved={})",
                parent.getSendRequestId(), parentStatus, debitable, parent.getReservedCredits());

        // Publish SmsFinalisedEvent so SmsFinalisedListener can create webhook delivery rows.
        // @TransactionalEventListener(AFTER_COMMIT) ensures delivery rows are only written
        // after this outer transaction commits successfully.
        List<SmsFinalisedEvent.RecipientSummary> summaries = recipients.stream()
                .map(r -> new SmsFinalisedEvent.RecipientSummary(
                        r.getRecipient(),
                        r.getGatewayMessageId(),
                        r.getSendStatus()
                ))
                .toList();
        eventPublisher.publishEvent(new SmsFinalisedEvent(parent.getClientId(), parent.getSendRequestId(), summaries));
    }

    private int parseSegmentsConsumed(NexahDrEntry dlr) {
        if (dlr.totalSmsUnit() == null || dlr.totalSmsUnit().isBlank()) {
            log.warn("DR for messageId={} has null/blank totalSmsUnit — defaulting to 1", dlr.messageId());
            return 1;
        }
        try {
            return Integer.parseInt(dlr.totalSmsUnit().trim());
        } catch (NumberFormatException e) {
            log.warn("DR for messageId={} has unparseable totalSmsUnit='{}' — defaulting to 1",
                    dlr.messageId(), dlr.totalSmsUnit());
            return 1;
        }
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
