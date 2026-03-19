package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsRecoveryWorker {

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Recovery logic for a single stuck SENDING request.
     * Note: No @Transactional here; called within a programmatic transaction.
     */
    public void recoverStuckSending(SendRequest request) {
        List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(request.getId());
        boolean hasSentRecipients = recipients.stream().anyMatch(r -> r.getGatewayMessageId() != null);

        if (hasSentRecipients) {
            log.info("Recovery: Found stuck SENDING request {} with saved provider IDs. Transitioning to SUBMITTED.",
                    request.getSendRequestId());
            request.setSendStatus(SendRequestStatus.SUBMITTED);
        } else {
            log.warn("Recovery: Reverting stuck SENDING request {} to ACCEPTED for retry.",
                    request.getSendRequestId());
            request.setSendStatus(SendRequestStatus.ACCEPTED);
        }
        sendRequestRepository.save(request);
    }

    /**
     * Force-finalizes a stale SUBMITTED request.
     * Note: No @Transactional here; called within a programmatic transaction.
     */
    public void forceFinalize(SendRequest parent) {
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

    public List<SendRequest> grabStuckSendingBatch(Instant cutoff, int limit) {
        return sendRequestRepository.findAndLockStuckSendingRequests(cutoff, limit);
    }

    public List<SendRequest> grabStaleSubmittedBatch(Instant cutoff, int limit) {
        return sendRequestRepository.findAndLockStaleSubmittedRequests(cutoff, limit);
    }
}
