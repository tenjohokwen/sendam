package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.CancelSmsResponse;
import com.softropic.sendam.client.contract.MessageStatusEntry;
import com.softropic.sendam.client.contract.MessageStatusResponse;
import com.softropic.sendam.client.contract.SendRequestStatus;
import com.softropic.sendam.client.contract.SendSmsRequest;
import com.softropic.sendam.client.contract.SendSmsResponse;
import com.softropic.sendam.client.contract.exception.CancelNotAllowedException;
import com.softropic.sendam.client.contract.exception.ProviderUnavailableException;
import com.softropic.sendam.client.contract.exception.RateLimitExceededException;
import com.softropic.sendam.client.contract.exception.SmsError;
import com.softropic.sendam.client.contract.exception.SmsValidationException;
import com.softropic.sendam.client.repo.SendRequest;
import com.softropic.sendam.client.repo.SendRequestRecipient;
import com.softropic.sendam.client.repo.SendRequestRecipientRepository;
import com.softropic.sendam.client.repo.SendRequestRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.common.validation.CamMobileValidator;
import com.softropic.sendam.security.contract.util.RateLimited;
import com.softropic.sendam.security.service.RateLimitingService;

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

    private static final Pattern SENDER_ID_PATTERN = Pattern.compile("^[A-Z0-9]{1,11}$");

    private final SendRequestRepository sendRequestRepo;
    private final SendRequestRecipientRepository recipientRepo;
    private final CreditReservationService creditReservationService;
    private final CreditService creditService;
    private final RateLimitingService rateLimitingService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

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

        // Step 3: Validate sender ID
        if (!SENDER_ID_PATTERN.matcher(request.sender()).matches()) {
            throw new SmsValidationException(
                    "Invalid sender ID: must be 1-11 uppercase alphanumeric characters",
                    SmsError.INVALID_SENDER_ID);
        }

        // Step 4: Validate message (JSR-303 @NotBlank handles null/blank at DTO level,
        // but defend here as well per plan instruction)
        if (request.message() == null || request.message().isBlank()) {
            throw new SmsValidationException("Message must not be blank", SmsError.INVALID_SENDER_ID);
        }

        // Step 5: Validate all recipients — collect ALL failures before throwing
        List<String> invalid = request.recipients().stream()
                .filter(phone -> !isValidRecipient(phone))
                .toList();
        if (!invalid.isEmpty()) {
            throw new SmsValidationException(
                    "Invalid recipient phone numbers: " + invalid,
                    SmsError.INVALID_PHONE_NUMBER);
        }

        // Step 6: Validate scheduleTime (only if present — must be in the future)
        if (request.scheduleTime() != null && !request.scheduleTime().isAfter(Instant.now())) {
            throw new SmsValidationException(
                    "scheduleTime must be in the future",
                    SmsError.INVALID_SCHEDULE_TIME);
        }

        // Step 6.5: Check provider availability before reserving credits (PROVIDER-01)
        // Circuit breaker state check is in-memory — no network call.
        // Reject both OPEN (fully tripped) and HALF_OPEN (probe in progress) to avoid
        // reserving credits for requests that the dispatcher will likely fail.
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("nexah");
        if (cb.getState() == CircuitBreaker.State.OPEN || cb.getState() == CircuitBreaker.State.HALF_OPEN) {
            throw new ProviderUnavailableException("SMS provider is currently unavailable");
        }

        // Step 7: Calculate segments and total credits to reserve
        int segmentCount = SmsSegmentCalculator.calculate(request.message());
        long totalCredits = (long) segmentCount * recipientCount;

        // Step 8: Reserve credits — throws InsufficientBalanceException if insufficient
        // (handled by existing ApiAdvice handler → 400 INSUFFICIENT_CLIENT_BALANCE)
        String reference = "sms:" + request.sendRequestId();
        long reservationId = creditReservationService.reserve(clientId, totalCredits, reference);

        // Step 9: Persist SendRequest row
        SendRequest sendRequest = SendRequest.builder()
                .clientId(clientId)
                .sendRequestId(request.sendRequestId())
                .sender(request.sender())
                .message(request.message())
                .sendStatus(SendRequestStatus.ACCEPTED)
                .scheduleTime(request.scheduleTime())
                .messageCount(recipientCount)
                .segmentCount(segmentCount)
                .reservedCredits(totalCredits)
                .reservationId(reservationId)
                .status(EntityStatus.ACTIVE)
                .build();
        sendRequestRepo.save(sendRequest);

        // Step 10: Persist one SendRequestRecipient row per recipient
        request.recipients().forEach(phone ->
                recipientRepo.save(SendRequestRecipient.builder()
                        .sendRequestIdFk(sendRequest.getId())
                        .clientId(clientId)
                        .recipient(phone)
                        .sendStatus(SendRequestStatus.ACCEPTED)
                        .status(EntityStatus.ACTIVE)
                        .build())
        );

        // Step 11: Read balance AFTER reservation for response
        long balanceAfter = creditService.getBalance(clientId).availableBalance();

        // Step 12: Return response
        log.debug("SMS send accepted for clientId={}, sendRequestId={}, reservedCredits={}", clientId, request.sendRequestId(), totalCredits);
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

        if (request.getSendStatus() != SendRequestStatus.ACCEPTED || request.getScheduleTime() == null) {
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
