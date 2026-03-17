package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for SMS requests due for dispatch and submits them to a provider via SmsSender.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsSchedulerService {

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;
    private final List<SmsSender> smsSenders; // Autowires all SmsSender implementations
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Dispatches both due scheduled requests and pending immediate requests.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchScheduledMessages() {
        Instant now = Instant.now();

        List<SendRequest> due = sendRequestRepository.findDueScheduledRequests(now);
        List<SendRequest> immediate = sendRequestRepository.findPendingImmediateRequests();

        List<SendRequest> toDispatch = new ArrayList<>();
        toDispatch.addAll(due);
        toDispatch.addAll(immediate);

        if (toDispatch.isEmpty()) {
            return;
        }

        if (smsSenders.isEmpty()) {
            log.error("No SmsSender implementations found! Cannot dispatch messages.");
            return;
        }

        // For v1, we just use the first available sender (Nexah)
        SmsSender sender = smsSenders.get(0);

        log.info("Dispatching {} SMS request(s) using {} ({} scheduled-due, {} immediate)",
                toDispatch.size(), sender.getClass().getSimpleName(), due.size(), immediate.size());

        for (SendRequest request : toDispatch) {
            try {
                List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(request.getId());
                
                sender.send(request, recipients);

                boolean anySubmitted = false;
                for (SendRequestRecipient recipient : recipients) {
                    if (recipient.getSendStatus() == SendRequestStatus.SUBMITTED) {
                        recipientRepository.save(recipient);
                        anySubmitted = true;
                    }
                }

                if (anySubmitted) {
                    request.setSendStatus(SendRequestStatus.SUBMITTED);
                    sendRequestRepository.save(request);
                    log.info("Dispatched sendRequestId={} — {} recipient(s) transitioned to SUBMITTED",
                            request.getSendRequestId(), recipients.size());
                }
            } catch (ProviderUnavailableException e) {
                log.warn("Provider unavailable for sendRequestId={}. Will retry on next cycle.",
                        request.getSendRequestId());
            } catch (Exception e) {
                log.error("Failed to dispatch sendRequestId={}", request.getSendRequestId(), e);
            }
        }
    }

    /**
     * Force-finalizes requests that have been stuck in SUBMITTED state for 24+ hours.
     * Logic is now moved to SmsProviderReportListener via a ForceFinalizeEvent or similar,
     * but for simplicity in this phase, we keep it here and call repositories directly
     * since they belong to the SAME module (sms).
     * 
     * Refactoring Note: recoverStaleSms no longer calls Nexah's DrCallbackService.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void recoverStaleSms() {
        Instant cutoff = Instant.now().minusSeconds(86_400); // 24 hours ago
        List<SendRequest> stale = sendRequestRepository.findStaleSubmittedRequests(cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.warn("Stale SMS recovery: force-finalizing {} request(s) stuck in SUBMITTED for 24+ hours", stale.size());

        for (SendRequest request : stale) {
            try {
                forceFinalize(request);
            } catch (Exception e) {
                log.error("Failed to force-finalize stale sendRequestId={}", request.getSendRequestId(), e);
            }
        }
    }

    private void forceFinalize(SendRequest parent) {
        List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(parent.getId());

        for (SendRequestRecipient recipient : recipients) {
            if (recipient.getSendStatus() == SendRequestStatus.SUBMITTED) {
                int estimatedSegments = Math.max(1, parent.getSegmentCount());
                recipient.setSegmentsConsumed(estimatedSegments);
                recipient.setSendStatus(SendRequestStatus.FAILED);
                recipientRepository.save(recipient);
            }
        }
        
        parent.setSendStatus(SendRequestStatus.FAIL_FINALIZED);
        parent.setFinalizedAt(Instant.now());
        sendRequestRepository.save(parent);

        eventPublisher.publishEvent(new SmsFinalisedEvent(
            parent.getClientId(),
            parent.getSendRequestId(),
            List.of(),
            parent.getReservationId(),
            0L
        ));

        log.info("SendRequest {} force-finalized as FAIL_FINALIZED", parent.getSendRequestId());
    }
}
