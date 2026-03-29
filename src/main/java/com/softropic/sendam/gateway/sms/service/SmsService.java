package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.sms.contract.CancelSmsResponse;
import com.softropic.sendam.gateway.sms.contract.MessageStatusEntry;
import com.softropic.sendam.gateway.sms.contract.MessageStatusResponse;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SendSmsRequest;
import com.softropic.sendam.gateway.sms.contract.SendSmsResponse;
import com.softropic.sendam.gateway.sms.contract.CancelNotAllowedException;
import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.account.contract.RateLimitExceededException;
import com.softropic.sendam.gateway.sms.contract.SmsError;
import com.softropic.sendam.gateway.sms.contract.SmsValidationException;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import com.softropic.sendam.gateway.billing.service.CreditReservationService;
import com.softropic.sendam.gateway.billing.service.CreditService;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.common.validation.CamMobileValidator;
import com.softropic.sendam.security.contract.util.RateLimited;
import com.softropic.sendam.security.service.RateLimitingService;

import org.springframework.context.ApplicationEventPublisher;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the full SMS send lifecycle: idempotency check, rate limiting,
 * validation, credit reservation, and persistence.
 *
 * <p>All validation steps execute before {@link CreditReservationService#reserve} —
 * credits are never touched when input is invalid.
 *
 * <p>The class is {@link Transactional} so that credit reservation and row persistence
 * participate in a single transaction: either both commit or both roll back.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private static final String SMS_SENDER = "SENDAM"; //TODO this needs to be whitelisted

    private final SendRequestRepository sendRequestRepo;
    private final SendRequestRecipientRepository recipientRepo;
    private final CreditReservationService creditReservationService;
    private final CreditService creditService;
    private final RateLimitingService rateLimitingService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Sends an SMS request for the given client, enforcing idempotency, rate limits,
     * input validation, and credit reservation in the documented order.
     *
     * @param clientId the authenticated client
     * @param request  the send request payload
     * @return a {@link SendSmsResponse} with reservation details and status ACCEPTED
     */
    @RateLimited(key = "sms_send", capacity = 10, duration = 1, unit = TimeUnit.SECONDS)
    public SendSmsResponse sendSms(Long clientId, SendSmsRequest request) {

        // Step 1: Idempotency — return original response if already processed
        Optional<SendRequest> existing = sendRequestRepo.findByClientIdAndSendRequestId(clientId, request.sendRequestId());
        if (existing.isPresent()) {
            log.debug("Idempotent send for clientId={}, sendRequestId={}", clientId, request.sendRequestId());
            long currentBalance = creditService.getBalance(clientId).availableBalance();
            return toResponse(existing.get(), currentBalance);
        }

        // Step 2: N-token rate limit for recipients/min (AUTH-05)
        int recipientCount = request.recipients().size();
        boolean recipientsAllowed = rateLimitingService.tryConsume(
                String.valueOf(clientId),
                "sms_recipients",
                1000L,
                1L,
                TimeUnit.MINUTES,
                recipientCount
        );
        if (!recipientsAllowed) {
            log.warn("Recipient rate limit exceeded for clientId={}, recipientCount={}", clientId, recipientCount);
            throw new RateLimitExceededException(
                    "Recipient rate limit exceeded: max 1000 recipients per minute");
        }

        // Step 3: Validate message (JSR-303 @NotBlank handles null/blank at DTO level,
        // but defend here as well per plan instruction)
        if (request.message() == null || request.message().isBlank()) {
            throw new SmsValidationException("Message must not be blank", SmsError.INVALID_MESSAGE);
        }

        // Step 4: Validate all recipients — collect ALL failures before throwing
        List<String> invalid = request.recipients().stream()
                .filter(phone -> !isValidRecipient(phone))
                .toList();
        if (!invalid.isEmpty()) {
            throw new SmsValidationException(
                    "Invalid recipient phone numbers: " + invalid,
                    SmsError.INVALID_PHONE_NUMBER);
        }

        // Step 5: Validate scheduleTime (only if present — must be in the future)
        if (request.scheduleTime() != null && !request.scheduleTime().isAfter(Instant.now())) {
            throw new SmsValidationException(
                    "scheduleTime must be in the future",
                    SmsError.INVALID_SCHEDULE_TIME);
        }

        // Step 5.5: Check provider availability before reserving credits (PROVIDER-01)
        // Circuit breaker state check is in-memory — no network call.
        // Reject both OPEN (fully tripped) and HALF_OPEN (probe in progress) to avoid
        // reserving credits for requests that the dispatcher will likely fail.
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
        if (cb.getState() == CircuitBreaker.State.OPEN || cb.getState() == CircuitBreaker.State.HALF_OPEN) {
            throw new ProviderUnavailableException("SMS provider is currently unavailable");
        }

        // Step 6: Calculate segments and reservation amounts (RESV-01, RESV-02, RESV-03)
        // expectedSegments: per-recipient segment count from GSM-7/UCS-2 formula (RESV-01)
        int expectedSegments = SmsSegmentCalculator.calculate(request.message());
        // rawExpectedCredits: unbuffered expected total — stored for deviation comparison (RESV-03)
        long rawExpectedCredits = (long) expectedSegments * recipientCount;
        // reservationAmount: +1 buffer per recipient guards against Nexah over-reporting (RESV-02)
        long reservationAmount = (long) (expectedSegments + 1) * recipientCount;

        // Step 7: Reserve credits using buffered amount — throws InsufficientBalanceException if insufficient
        // (handled by existing ApiAdvice handler → 400 INSUFFICIENT_CLIENT_BALANCE)
        String reference = "sms:" + request.sendRequestId();
        long reservationId = creditReservationService.reserve(clientId, reservationAmount, reference);

        // Step 8: Persist SendRequest row
        SendRequest sendRequest = SendRequest.builder()
                .clientId(clientId)
                .sendRequestId(request.sendRequestId())
                .sender(SMS_SENDER)
                .message(request.message())
                .sendStatus(SendRequestStatus.ACCEPTED)
                .scheduleTime(request.scheduleTime())
                .messageCount(recipientCount)
                .segmentCount(expectedSegments)
                .reservedCredits(reservationAmount)        // buffered amount (RESV-02)
                .rawExpectedCredits(rawExpectedCredits)    // unbuffered amount (RESV-03)
                .reservationId(reservationId)
                .status(EntityStatus.ACTIVE)
                .build();
        sendRequestRepo.save(sendRequest);

        // Audit: record submission on the first-submission path only (not the idempotent early-return above)
        eventPublisher.publishEvent(new DomainAuditEvent(
            AuditEventType.SMS_SEND_SUBMITTED,
            clientId,
            "client:" + clientId,
            "SMS submitted: sendRequestId=" + request.sendRequestId() + ", recipients=" + recipientCount
        ));

        // Step 9: Persist one SendRequestRecipient row per recipient (RESV-04: store expectedSegments)
        request.recipients().forEach(phone ->
                recipientRepo.save(SendRequestRecipient.builder()
                        .sendRequestIdFk(sendRequest.getId())
                        .clientId(clientId)
                        .recipient(phone)
                        .sendStatus(SendRequestStatus.ACCEPTED)
                        .expectedSegments(expectedSegments)    // RESV-04: per-recipient for Phase 22 deviation
                        .status(EntityStatus.ACTIVE)
                        .build())
        );

        // Step 10: Read balance AFTER reservation for response
        long balanceAfter = creditService.getBalance(clientId).availableBalance();

        // Step 11: Return response
        log.debug("SMS send accepted for clientId={}, sendRequestId={}, reservedCredits={}", clientId, request.sendRequestId(), reservationAmount);
        return toResponse(sendRequest, balanceAfter);
    }

    /**
     * Returns paginated per-recipient status for a previously submitted send request.
     *
     * @param clientId      the authenticated client
     * @param sendRequestId the caller-supplied send request identifier
     * @param page          zero-based page index
     * @param pageSize      maximum entries per page (clamped to 200)
     * @return {@link MessageStatusResponse} with per-recipient delivery states
     */
    @Transactional(readOnly = true)
    public MessageStatusResponse getStatus(Long clientId, String sendRequestId, int page, int pageSize) {
        SendRequest req = sendRequestRepo.findByClientIdAndSendRequestId(clientId, sendRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Send request not found: " + sendRequestId,
                        "send_request"));

        int effectivePageSize = Math.min(pageSize, 200);

        List<SendRequestRecipient> allRecipients =
                recipientRepo.findBySendRequestIdFkAndClientId(req.getId(), clientId);

        int fromIndex = page * effectivePageSize;
        int toIndex = Math.min(fromIndex + effectivePageSize, allRecipients.size());
        List<SendRequestRecipient> paged = fromIndex >= allRecipients.size()
                ? List.of()
                : allRecipients.subList(fromIndex, toIndex);

        List<MessageStatusEntry> entries = paged.stream()
                .map(r -> new MessageStatusEntry(
                        r.getRecipient(),
                        r.getSendStatus().name(),
                        r.getGatewayMessageId(),
                        r.getProviderMessageId(),
                        r.getSegmentsConsumed()))
                .toList();

        return new MessageStatusResponse(
                sendRequestId,
                req.getSendStatus().name(),
                page,
                effectivePageSize,
                allRecipients.size(),
                entries);
    }

    /**
     * Cancels an ACCEPTED scheduled SMS request and releases the reserved credits.
     *
     * <p>Cancel is allowed ONLY when the request is in ACCEPTED status AND has a
     * non-null scheduleTime. Immediate (non-scheduled) sends and already-submitted
     * or finalized requests cannot be cancelled.
     *
     * @param clientId      the authenticated client
     * @param sendRequestId the caller-supplied send request identifier
     * @return a {@link CancelSmsResponse} with status CANCELLED
     * @throws ResourceNotFoundException if the send request does not exist for this client
     * @throws CancelNotAllowedException if cancellation is not permitted (status not ACCEPTED or not scheduled)
     */
    public CancelSmsResponse cancelScheduled(Long clientId, String sendRequestId) {
        SendRequest request = sendRequestRepo.findByClientIdAndSendRequestId(clientId, sendRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Send request not found",
                        "send_request"));

        // CFREEZE-04: allow cancellation of SUSPENDED scheduled SMS (frozen state) in addition to ACCEPTED
        boolean statusAllowed = request.getSendStatus() == SendRequestStatus.ACCEPTED
                || request.getSendStatus() == SendRequestStatus.SUSPENDED;
        if (!statusAllowed || request.getScheduleTime() == null) {
            String reason = request.getScheduleTime() == null
                    ? " and is not a scheduled request"
                    : "";
            throw new CancelNotAllowedException(
                    "Cannot cancel: request status is " + request.getSendStatus().name() + reason);
        }

        creditReservationService.release(clientId, request.getReservationId());

        request.setSendStatus(SendRequestStatus.CANCELLED);
        sendRequestRepo.save(request);

        log.debug("Cancelled scheduled request {} for clientId={}", sendRequestId, clientId);
        return new CancelSmsResponse(sendRequestId, "CANCELLED");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SendSmsResponse toResponse(SendRequest req, long balanceAfter) {
        return new SendSmsResponse(
                "req_" + req.getId(),
                req.getSendRequestId(),
                req.getMessageCount(),
                req.getSegmentCount(),
                req.getReservedCredits(),
                balanceAfter,
                req.getSendStatus().name());
    }

    /**
     * Returns {@code true} if {@code phone} is a valid Cameroon mobile number.
     * Swallows {@link CamMobileValidator.InvalidMobileNumberException} — invalid numbers
     * are collected in a batch and reported together.
     */
    private boolean isValidRecipient(String phone) {
        try {
            CamMobileValidator.validate(phone);
            return true;
        } catch (CamMobileValidator.InvalidMobileNumberException e) {
            return false;
        }
    }
}
