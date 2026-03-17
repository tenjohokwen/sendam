package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.gateway.account.service.ClientFreezeService;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalance;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlert;
import com.softropic.sendam.gateway.billing.service.SegmentDeviationService.SegmentDeviationAlertData;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates post-send billing finalization using Nexah's actual segment totals.
 * Implements the 6 BOOK scenarios (BOOK-01 through BOOK-06) and creates SEGMENT or
 * PLATFORM_FREEZE deviation alerts for any non-zero deviation.
 *
 * <p>Lock order: (1) reservation row [via CreditReservationService.debit()],
 * (2) client credit balance [via ClientCreditBalanceRepository.findByClientIdForUpdate()],
 * (3) platform credit balance [via PlatformCreditBalanceRepository.findForUpdate()].
 * Never invert this order.
 *
 * <p>Cross-module service dependency: injects ClientFreezeService (gateway.account.service)
 * for BOOK-05/06 client freeze. This is permitted per ARCHITECTURE.md — service-to-service
 * injection is an accepted orchestration pattern (same precedent as CreditReservationService).
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class FinalBookingService {

    private final CreditReservationService creditReservationService;
    private final CreditService creditService;
    private final PlatformCreditService platformCreditService;
    private final ClientFreezeService clientFreezeService;
    private final PlatformFreezeService platformFreezeService;
    private final SegmentDeviationService segmentDeviationService;
    private final ClientCreditBalanceRepository clientCreditBalanceRepository;
    private final PlatformCreditBalanceRepository platformCreditBalanceRepository;
    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;

    /**
     * Finalizes billing for a completed SMS send request.
     *
     * @param clientId            the client whose credits to settle
     * @param sendRequestId       human-readable send request reference
     * @param actualSegmentsTotal total segments actually consumed by Nexah (0 = all failed)
     * @param reservationId       ledger entry id of the SMS_RESERVATION (null = no billing)
     */
    public void book(Long clientId, String sendRequestId, long actualSegmentsTotal, Long reservationId) {
        // Step 1: Load SendRequest
        SendRequest sendRequest = sendRequestRepository
                .findByClientIdAndSendRequestId(clientId, sendRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SendRequest not found: clientId=" + clientId + ", sendRequestId=" + sendRequestId,
                        "send_request"));

        if (reservationId == null) {
            log.warn("No reservationId found for sendRequestId={} — skipping billing finalization", sendRequestId);
            return;
        }

        // Step 2: Zero segments — all failed before dispatch
        if (actualSegmentsTotal == 0) {
            creditReservationService.release(clientId, reservationId);
            log.info("Released reservation {} for sendRequestId={} (zero actual segments)", reservationId, sendRequestId);
            return;
        }

        // Step 3: Compute totals and delta
        long expectedTotal = sendRequest.getRawExpectedCredits();
        long reservedTotal = sendRequest.getReservedCredits();
        long delta = actualSegmentsTotal - expectedTotal;

        log.info("Booking sendRequestId={}: expected={}, reserved={}, actual={}, delta={}",
                sendRequestId, expectedTotal, reservedTotal, actualSegmentsTotal, delta);

        // Step 4: Determine scenario and execute
        if (actualSegmentsTotal <= reservedTotal) {
            // BOOK-01/02/03: actual fits within reservation
            creditReservationService.debit(clientId, reservationId, actualSegmentsTotal);

            if (delta == 0) {
                // BOOK-02: exact match — no deviation alert
                log.info("BOOK-02: exact segment match for sendRequestId={}", sendRequestId);
                return;
            }

            // BOOK-01 (delta < 0, refund) or BOOK-03 (delta > 0, within buffer)
            String scenario = delta < 0 ? "BOOK-01" : "BOOK-03";
            log.info("{}: deviation alert for sendRequestId={}, delta={}", scenario, sendRequestId, delta);
            List<SegmentDeviationAlert.RecipientDeviationEntry> breakdown = buildBreakdown(sendRequest.getId());
            segmentDeviationService.createAlert(new SegmentDeviationAlertData(
                    sendRequest.getId(),
                    sendRequestId,
                    clientId,
                    DeviationAlertType.SEGMENT,
                    expectedTotal,
                    actualSegmentsTotal,
                    delta,
                    null,
                    false,
                    false,
                    null,
                    breakdown,
                    "REFUNDED"
            ));

        } else {
            // BOOK-04/05/06: actual exceeds reservation — need to charge extra
            long extra = actualSegmentsTotal - reservedTotal;

            // Acquire client balance lock BEFORE debit() to read available balance atomically
            // Lock order (2): client credit balance — after (1) reservation row below
            ClientCreditBalance clientLock = clientCreditBalanceRepository
                    .findByClientIdForUpdate(clientId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No credit balance row for client: " + clientId,
                            "client_credit_balance"));
            long clientAvailable = clientLock.getBalance();

            // Consume the full reservation (lock order 1: reservation row inside debit())
            // Note: debit() will also lock the client balance row; same transaction = re-entrant lock
            creditReservationService.debit(clientId, reservationId, reservedTotal);

            if (clientAvailable >= extra) {
                // BOOK-04: client has enough for the extra — direct extra debit
                creditService.applyLedgerEntry(
                        clientId,
                        LedgerEntryType.SMS_EXTRA_DEBIT,
                        -extra,
                        "extra-debit:" + sendRequestId);

                log.info("BOOK-04: extra debit {} for sendRequestId={}", extra, sendRequestId);
                List<SegmentDeviationAlert.RecipientDeviationEntry> breakdown = buildBreakdown(sendRequest.getId());
                segmentDeviationService.createAlert(new SegmentDeviationAlertData(
                        sendRequest.getId(),
                        sendRequestId,
                        clientId,
                        DeviationAlertType.SEGMENT,
                        expectedTotal,
                        actualSegmentsTotal,
                        delta,
                        null,
                        false,
                        false,
                        null,
                        breakdown,
                        "EXTRA_DEBITED"
                ));

            } else {
                // BOOK-05/06: client cannot cover the extra — shortfall absorption path
                long shortfall = extra - clientAvailable;

                // Drain client balance to zero (only if there is anything to drain)
                if (clientAvailable > 0) {
                    creditService.applyLedgerEntry(
                            clientId,
                            LedgerEntryType.SMS_EXTRA_DEBIT,
                            -clientAvailable,
                            "extra-debit-drain:" + sendRequestId);
                }

                // Acquire platform balance lock (lock order 3: after client lock 2)
                PlatformCreditBalance platformLock = platformCreditBalanceRepository
                        .findForUpdate()
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Platform balance not initialized",
                                "platform_credit_balance"));
                long platformAvailable = platformLock.getBalance();

                // Freeze the client account — cannot cover extra from own balance
                clientFreezeService.freeze(clientId,
                        "Shortfall absorption: " + shortfall + " credits unreserved from sendRequestId=" + sendRequestId);

                List<SegmentDeviationAlert.RecipientDeviationEntry> breakdown = buildBreakdown(sendRequest.getId());

                if (platformAvailable >= shortfall) {
                    // BOOK-05: platform absorbs full shortfall
                    platformCreditService.applyLedgerEntry(
                            PlatformLedgerEntryType.SHORTFALL_ABSORPTION,
                            -shortfall,
                            "shortfall:" + sendRequestId);

                    log.info("BOOK-05: platform absorbed shortfall={} for sendRequestId={}", shortfall, sendRequestId);
                    segmentDeviationService.createAlert(new SegmentDeviationAlertData(
                            sendRequest.getId(),
                            sendRequestId,
                            clientId,
                            DeviationAlertType.SEGMENT,
                            expectedTotal,
                            actualSegmentsTotal,
                            delta,
                            shortfall,
                            true,
                            false,
                            null,
                            breakdown,
                            "SHORTFALL_ABSORBED"
                    ));

                } else {
                    // BOOK-06: platform cannot absorb full shortfall — partial absorption, platform freeze
                    long unrecovered = shortfall - platformAvailable;

                    // Absorb only what the platform has (never pass -shortfall; would throw InsufficientPlatformBalanceException)
                    if (platformAvailable > 0) {
                        platformCreditService.applyLedgerEntry(
                                PlatformLedgerEntryType.SHORTFALL_ABSORPTION,
                                -platformAvailable,
                                "shortfall-partial:" + sendRequestId);
                    }

                    platformFreezeService.freeze(
                            "Platform shortfall: unrecovered=" + unrecovered + " from sendRequestId=" + sendRequestId,
                            unrecovered);

                    log.warn("BOOK-06: platform frozen, unrecovered={} for sendRequestId={}", unrecovered, sendRequestId);

                    // SEGMENT alert (client deviation record)
                    segmentDeviationService.createAlert(new SegmentDeviationAlertData(
                            sendRequest.getId(),
                            sendRequestId,
                            clientId,
                            DeviationAlertType.SEGMENT,
                            expectedTotal,
                            actualSegmentsTotal,
                            delta,
                            shortfall,
                            true,
                            true,
                            unrecovered,
                            breakdown,
                            "SHORTFALL_ABSORBED"
                    ));

                    // PLATFORM_FREEZE alert (platform-level incident record)
                    segmentDeviationService.createAlert(new SegmentDeviationAlertData(
                            sendRequest.getId(),
                            sendRequestId,
                            clientId,
                            DeviationAlertType.PLATFORM_FREEZE,
                            expectedTotal,
                            actualSegmentsTotal,
                            delta,
                            unrecovered,
                            true,
                            true,
                            unrecovered,
                            breakdown,
                            "SHORTFALL_ABSORBED"
                    ));
                }
            }
        }
    }

    /**
     * Loads per-recipient segment data and maps to deviation entries for the JSONB breakdown.
     */
    private List<SegmentDeviationAlert.RecipientDeviationEntry> buildBreakdown(Long sendRequestPk) {
        List<SendRequestRecipient> recipients = recipientRepository.findBySendRequestIdFk(sendRequestPk);
        return recipients.stream()
                .map(r -> {
                    int actual = r.getSegmentsConsumed() != null ? r.getSegmentsConsumed() : 0;
                    return new SegmentDeviationAlert.RecipientDeviationEntry(
                            r.getRecipient(),
                            r.getExpectedSegments(),
                            actual,
                            actual - r.getExpectedSegments()
                    );
                })
                .toList();
    }
}
