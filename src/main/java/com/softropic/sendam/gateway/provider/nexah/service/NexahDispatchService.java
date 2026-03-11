package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahProperties;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahSendRequest;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahSendResponse;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahSmsEntry;
import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Submits an ACCEPTED SendRequest to the Nexah SMS provider and transitions
 * recipients to SUBMITTED with their assigned gateway_message_id.
 *
 * <p>Called by SmsSchedulerService for both immediate and scheduled sends.
 * On ProviderUnavailableException, the exception is re-thrown so the scheduler
 * can catch it per-request and continue with remaining requests.
 *
 * <p>If a recipient's mobileNo is not found in the Nexah response (partial
 * dispatch edge case), that recipient is left in ACCEPTED state for recovery
 * by the stale-SUBMITTED job.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class NexahDispatchService {

    private final NexahClient nexahClient;
    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;
    private final NexahProperties nexahProperties;

    /**
     * Dispatches a SendRequest to Nexah and advances recipients to SUBMITTED.
     *
     * @param request the ACCEPTED send request to dispatch
     * @throws ProviderUnavailableException if the Nexah circuit breaker is OPEN or the call fails
     */
    public void dispatch(SendRequest request) {
        List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(request.getId());

        String mobilesString = recipients.stream()
                .map(SendRequestRecipient::getRecipient)
                .collect(Collectors.joining(","));

        NexahSendRequest nexahRequest = new NexahSendRequest(
                nexahProperties.user(),
                nexahProperties.password(),
                nexahProperties.senderid(),
                request.getMessage(),
                mobilesString
        );

        // This call is wrapped by @CircuitBreaker in NexahClient.
        // If the circuit is OPEN, the fallback throws ProviderUnavailableException.
        NexahSendResponse response;
        try {
            response = nexahClient.sendSms(nexahRequest);
        } catch (ProviderUnavailableException e) {
            log.warn("Nexah provider unavailable for sendRequestId={}. Will retry on next scheduler cycle.",
                    request.getSendRequestId());
            throw e;
        }

        if (response == null || response.sms() == null) {
            log.warn("Nexah returned null or empty sms[] for sendRequestId={}. Request stays in ACCEPTED state.",
                    request.getSendRequestId());
            return;
        }

        // Build lookup map: mobileNo -> NexahSmsEntry (for safe matching regardless of order)
        Map<String, NexahSmsEntry> responseByMobile = response.sms().stream()
                .filter(entry -> entry.mobileNo() != null)
                .collect(Collectors.toMap(
                        NexahSmsEntry::mobileNo,
                        entry -> entry,
                        (existing, duplicate) -> existing // keep first on duplicate mobile
                ));

        boolean anySubmitted = false;
        for (SendRequestRecipient recipient : recipients) {
            NexahSmsEntry entry = responseByMobile.get(recipient.getRecipient());
            if (entry == null) {
                log.warn("Nexah response missing mobileNo={} for sendRequestId={}. Recipient stays ACCEPTED (will be recovered by stale job).",
                        recipient.getRecipient(), request.getSendRequestId());
                continue;
            }
            recipient.setGatewayMessageId(entry.messageId());
            recipient.setProviderMessageId(entry.smsClientId());
            recipient.setSendStatus(SendRequestStatus.SUBMITTED);
            recipientRepository.save(recipient);
            anySubmitted = true;
        }

        if (anySubmitted) {
            request.setSendStatus(SendRequestStatus.SUBMITTED);
            sendRequestRepository.save(request);
            log.info("Dispatched sendRequestId={} to Nexah — {} recipient(s) transitioned to SUBMITTED",
                    request.getSendRequestId(), recipients.size());
        } else {
            log.warn("No recipients were submitted for sendRequestId={} — parent stays ACCEPTED", request.getSendRequestId());
        }
    }
}
