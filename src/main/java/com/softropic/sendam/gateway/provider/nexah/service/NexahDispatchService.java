package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.provider.nexah.contract.*;
import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.service.SmsSender;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nexah implementation of {@link SmsSender}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NexahDispatchService implements SmsSender {

    private final NexahClient nexahClient;
    private final NexahProperties nexahProperties;
    private final SendRequestRecipientRepository recipientRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Transactional(readOnly = true)
    public CircuitBreakerHealthResponse getCircuitBreakerHealth() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
        CircuitBreaker.Metrics m = cb.getMetrics();
        return new CircuitBreakerHealthResponse(
            cb.getState().name(),
            m.getFailureRate(),
            m.getNumberOfFailedCalls(),
            m.getNumberOfSuccessfulCalls(),
            m.getNumberOfNotPermittedCalls()
        );
    }

    @Transactional(readOnly = true)
    public ProviderStatsResponse getProviderStats() {
        ProviderStatsRow row = recipientRepository.findProviderStats();
        double failureRate = row.getDrReceived() == 0
            ? 0.0
            : (double) row.getFailedCount() / row.getDrReceived() * 100.0;
        return new ProviderStatsResponse(
            row.getTotalSubmitted(),
            row.getDrReceived(),
            row.getFailedCount(),
            failureRate
        );
    }

    @Override
    public void send(SendRequest request, List<SendRequestRecipient> recipients) throws ProviderUnavailableException {
        String mobilesString = recipients.stream()
                .map(SendRequestRecipient::getRecipient)
                .collect(Collectors.joining(","));

        NexahSendRequest nexahRequest = new NexahSendRequest(
                nexahProperties.user(),
                nexahProperties.password(),
                request.getMessage(),
                mobilesString
        );

        NexahSendResponse response;
        try {
            response = nexahClient.sendSms(nexahRequest);
        } catch (ProviderUnavailableException e) {
            log.warn("Nexah provider unavailable for sendRequestId={}. Will retry on next scheduler cycle.",
                    request.getSendRequestId());
            throw e;
        }

        if (response == null || response.sms() == null) {
            log.warn("Nexah returned null or empty sms[] for sendRequestId={}.", request.getSendRequestId());
            return;
        }

        Map<String, NexahSmsEntry> responseByMobile = response.sms().stream()
                .filter(entry -> entry.mobileNo() != null)
                .collect(Collectors.toMap(
                        NexahSmsEntry::mobileNo,
                        entry -> entry,
                        (existing, duplicate) -> existing
                ));

        for (SendRequestRecipient recipient : recipients) {
            NexahSmsEntry entry = responseByMobile.get(recipient.getRecipient());
            if (entry != null) {
                recipient.setGatewayMessageId(entry.messageId());
                recipient.setProviderMessageId(entry.smsClientId());
                recipient.setSendStatus(SendRequestStatus.SUBMITTED);
            } else {
                log.warn("Nexah response missing mobileNo={} for sendRequestId={}.",
                        recipient.getRecipient(), request.getSendRequestId());
            }
        }
    }
}
