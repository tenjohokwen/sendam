package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsDispatchWorker {

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;

    /**
     * Grabs a batch of ACCEPTED requests and locks them for processing by marking them as SENDING.
     * Transactional scope is small: just fetch and update status.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SendRequest> grabBatch(Instant now, int limit) {
        List<SendRequest> requests = sendRequestRepository.findAndLockAcceptedRequests(now, limit);
        if (requests.isEmpty()) {
            return List.of();
        }

        for (SendRequest request : requests) {
            request.setSendStatus(SendRequestStatus.SENDING);
        }
        return sendRequestRepository.saveAll(requests);
    }

    /**
     * Persists the submission results (IDs and statuses) for recipients.
     * This is a critical step to ensure we don't lose the provider's message IDs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecipientStatuses(List<SendRequestRecipient> recipients) {
        recipientRepository.saveAll(recipients);
    }

    /**
     * Updates the parent request to SUBMITTED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markParentAsSubmitted(SendRequest request) {
        request.setSendStatus(SendRequestStatus.SUBMITTED);
        sendRequestRepository.save(request);
    }

    /**
     * Reverts the request to ACCEPTED if the provider was unavailable, allowing for a retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertToAccepted(SendRequest request) {
        request.setSendStatus(SendRequestStatus.ACCEPTED);
        sendRequestRepository.save(request);
        log.warn("Reverted sendRequestId={} to ACCEPTED for retry.", request.getSendRequestId());
    }

    /**
     * Reverts the request to ACCEPTED if an unexpected error occurred.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleError(SendRequest request, Exception e) {
        log.error("Failed to dispatch sendRequestId={}. Reverting to ACCEPTED.", request.getSendRequestId(), e);
        request.setSendStatus(SendRequestStatus.ACCEPTED);
        sendRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<SendRequestRecipient> getRecipients(Long requestId) {
        return recipientRepository.findBySendRequestIdFk(requestId);
    }
}
