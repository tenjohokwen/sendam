package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.account.service.ClientFreezeService;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalance;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.service.SegmentDeviationService.SegmentDeviationAlertData;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FinalBookingService — covers all 6 BOOK scenarios (BOOK-01 through BOOK-06),
 * the zero-segments release path, and the SEGDEV per-recipient breakdown verification.
 *
 * <p>Naming convention mirrors SmsFinalisedBillingListenerTest: method_scenario_expectedBehaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinalBookingServiceTest {

    // --- constants ----------------------------------------------------------
    private static final Long CLIENT_ID = 100L;
    private static final String SEND_REQUEST_ID = "req-abc";
    private static final Long RESERVATION_ID = 42L;
    private static final Long SEND_REQUEST_PK = 77L;

    /**
     * 2 recipients × 2 expected segments each = 4 raw expected credits.
     * Buffer formula: (2+1) × 2 = 6 reserved credits.
     */
    private static final long RAW_EXPECTED_CREDITS = 4L;
    private static final long RESERVED_CREDITS = 6L;

    // --- mocks --------------------------------------------------------------
    @Mock private CreditReservationService creditReservationService;
    @Mock private CreditService creditService;
    @Mock private PlatformCreditService platformCreditService;
    @Mock private ClientFreezeService clientFreezeService;
    @Mock private PlatformFreezeService platformFreezeService;
    @Mock private SegmentDeviationService segmentDeviationService;
    @Mock private ClientCreditBalanceRepository clientCreditBalanceRepository;
    @Mock private PlatformCreditBalanceRepository platformCreditBalanceRepository;
    @Mock private SendRequestRepository sendRequestRepository;
    @Mock private SendRequestRecipientRepository recipientRepository;

    @InjectMocks
    private FinalBookingService finalBookingService;

    // --- shared stubs -------------------------------------------------------
    private SendRequest sendRequest;
    private SendRequestRecipient recipient1;
    private SendRequestRecipient recipient2;

    @BeforeEach
    void setUp() {
        // Use Mockito.mock() so we can stub getId() (set by @Tsid at persist time,
        // not via builder — BaseEntity.id has no @Builder.Default).
        sendRequest = mock(SendRequest.class);
        when(sendRequest.getId()).thenReturn(SEND_REQUEST_PK);
        when(sendRequest.getRawExpectedCredits()).thenReturn(RAW_EXPECTED_CREDITS);
        when(sendRequest.getReservedCredits()).thenReturn(RESERVED_CREDITS);

        when(sendRequestRepository.findByClientIdAndSendRequestId(CLIENT_ID, SEND_REQUEST_ID))
                .thenReturn(Optional.of(sendRequest));

        // Two recipients, each expecting 2 segments
        recipient1 = SendRequestRecipient.builder()
                .recipient("+237600000001")
                .expectedSegments(2)
                .build();
        recipient2 = SendRequestRecipient.builder()
                .recipient("+237600000002")
                .expectedSegments(2)
                .build();

        when(recipientRepository.findBySendRequestIdFk(SEND_REQUEST_PK))
                .thenReturn(List.of(recipient1, recipient2));
    }

    // =========================================================================
    // BOOK-01: actual(3) < expected(4) ≤ reserved(6) — under-expected, refunds buffer
    // =========================================================================

    @Test
    @DisplayName("BOOK-01: actual segments under expected — debit actual, create SEGMENT alert with delta=-1")
    void book_book01_underExpected_refundsBuffer_createsSegmentAlert() {
        // actual=3, expected=4, delta=-1; fits within reservation(6)
        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 3L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 3L);
        verify(segmentDeviationService).createAlert(argThat(d ->
                d.delta() == -1
                && d.financialAction().equals("REFUNDED")
                && d.alertType() == DeviationAlertType.SEGMENT
                && d.clientFrozen() == false
                && d.shortfallAmount() == null
        ));
        verify(creditService, never()).applyLedgerEntry(any(), any(), anyLong(), anyString());
        verify(clientFreezeService, never()).freeze(any(), anyString());
    }

    // =========================================================================
    // BOOK-02: actual(4) == expected(4) — exact match, no alert
    // =========================================================================

    @Test
    @DisplayName("BOOK-02: actual segments equal expected — debit actual, no deviation alert created")
    void book_book02_exactExpected_noAlert() {
        // actual=4 == expected=4, delta=0; fits within reservation(6)
        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 4L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 4L);
        verify(segmentDeviationService, never()).createAlert(any());
        verify(clientFreezeService, never()).freeze(any(), anyString());
    }

    // =========================================================================
    // BOOK-03: actual(5) > expected(4), still ≤ reserved(6) — within buffer
    // =========================================================================

    @Test
    @DisplayName("BOOK-03: actual segments over expected but within reservation buffer — debit actual, create SEGMENT alert with delta=+1")
    void book_book03_overExpectedWithinBuffer_createsSegmentAlert() {
        // actual=5, expected=4, delta=+1; still within reservation(6)
        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 5L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 5L);
        verify(segmentDeviationService).createAlert(argThat(d ->
                d.delta() == 1
                && d.financialAction().equals("REFUNDED")
                && d.alertType() == DeviationAlertType.SEGMENT
        ));
        verify(clientFreezeService, never()).freeze(any(), anyString());
    }

    // =========================================================================
    // BOOK-04: actual(8) > reserved(6), extra=2, clientAvailable=10 — client covers extra
    // =========================================================================

    @Test
    @DisplayName("BOOK-04: actual exceeds reservation, client has sufficient balance — debit reservation + extra, create SEGMENT alert")
    void book_book04_overReserved_clientCovers_extraDebit_createsAlert() {
        // actual=8, reserved=6, extra=2, client balance=10 → client covers
        stubClientBalance(10L);

        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 8L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 6L);
        verify(creditService).applyLedgerEntry(
                eq(CLIENT_ID),
                eq(LedgerEntryType.SMS_EXTRA_DEBIT),
                eq(-2L),
                anyString());
        verify(segmentDeviationService).createAlert(argThat(d ->
                d.delta() == 4
                && d.financialAction().equals("EXTRA_DEBITED")
                && d.alertType() == DeviationAlertType.SEGMENT
                && d.shortfallAmount() == null
                && d.clientFrozen() == false
        ));
        verify(clientFreezeService, never()).freeze(any(), anyString());
    }

    // =========================================================================
    // BOOK-05: actual(8) > reserved(6), extra=2, clientAvailable=1, platform=5 → platform absorbs shortfall(1)
    // =========================================================================

    @Test
    @DisplayName("BOOK-05: client shortfall, platform absorbs full shortfall — drain client, freeze client, create SEGMENT alert")
    void book_book05_overReserved_clientShortfall_platformAbsorbs_clientFrozen_createsAlert() {
        // actual=8, reserved=6, extra=2, client=1 → shortfall=1, platform=5 ≥ 1
        stubClientBalance(1L);
        stubPlatformBalance(5L);

        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 8L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 6L);
        // drain client: -clientAvailable = -1
        verify(creditService).applyLedgerEntry(
                eq(CLIENT_ID),
                eq(LedgerEntryType.SMS_EXTRA_DEBIT),
                eq(-1L),
                anyString());
        // platform absorbs shortfall = 1
        verify(platformCreditService).applyLedgerEntry(
                eq(PlatformLedgerEntryType.SHORTFALL_ABSORPTION),
                eq(-1L),
                anyString());
        verify(clientFreezeService).freeze(eq(CLIENT_ID), anyString());
        verify(platformFreezeService, never()).freeze(anyString(), anyLong());
        verify(segmentDeviationService).createAlert(argThat(d ->
                d.shortfallAmount() == 1L
                && d.clientFrozen()
                && !d.platformFrozen()
                && d.financialAction().equals("SHORTFALL_ABSORBED")
                && d.alertType() == DeviationAlertType.SEGMENT
        ));
    }

    // =========================================================================
    // BOOK-06: actual(8) > reserved(6), extra=2, clientAvailable=0, platform=1 → partial absorption, platform frozen
    // =========================================================================

    @Test
    @DisplayName("BOOK-06: client and platform both short — partial platform absorption, freeze both, issue two alerts")
    void book_book06_overReserved_platformAlsoShortfall_bothFrozen_twoAlerts() {
        // actual=8, reserved=6, extra=2, client=0, platform=1 → shortfall=2, unrecovered=1
        stubClientBalance(0L);
        stubPlatformBalance(1L);

        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 8L, RESERVATION_ID);

        verify(creditReservationService).debit(CLIENT_ID, RESERVATION_ID, 6L);
        // client=0 → no drain call
        verify(creditService, never()).applyLedgerEntry(any(), any(), anyLong(), anyString());
        // platform absorbs only what it has: 1
        verify(platformCreditService).applyLedgerEntry(
                eq(PlatformLedgerEntryType.SHORTFALL_ABSORPTION),
                eq(-1L),
                anyString());
        verify(clientFreezeService).freeze(eq(CLIENT_ID), anyString());
        // unrecovered = shortfall(2) - platformAvailable(1) = 1
        verify(platformFreezeService).freeze(anyString(), eq(1L));
        // Two alerts: SEGMENT + PLATFORM_FREEZE
        verify(segmentDeviationService, times(2)).createAlert(any());
    }

    // =========================================================================
    // Zero segments: all-failed path — release reservation, no debit, no alert
    // =========================================================================

    @Test
    @DisplayName("Zero actual segments — release reservation instead of debit, no alert created")
    void book_zeroSegments_releasesReservation() {
        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 0L, RESERVATION_ID);

        verify(creditReservationService).release(CLIENT_ID, RESERVATION_ID);
        verify(creditReservationService, never()).debit(any(), any(), anyLong());
        verify(segmentDeviationService, never()).createAlert(any());
    }

    // =========================================================================
    // SEGDEV: per-recipient breakdown is populated correctly in alert data
    // =========================================================================

    @Test
    @DisplayName("Per-recipient breakdown — captured SegmentDeviationAlertData contains one entry per recipient with correct delta")
    void book_perRecipientBreakdown_populatedCorrectly() {
        // BOOK-01 scenario variant: both recipients used 1 segment (expected=2)
        // actual total = 2, expected = 4, delta = -2
        recipient1.setSegmentsConsumed(1);
        recipient2.setSegmentsConsumed(1);

        finalBookingService.book(CLIENT_ID, SEND_REQUEST_ID, 2L, RESERVATION_ID);

        ArgumentCaptor<SegmentDeviationAlertData> captor =
                ArgumentCaptor.forClass(SegmentDeviationAlertData.class);
        verify(segmentDeviationService).createAlert(captor.capture());

        SegmentDeviationAlertData captured = captor.getValue();
        assertThat(captured.perRecipientBreakdown()).hasSize(2);
        assertThat(captured.perRecipientBreakdown()).allSatisfy(entry -> {
            assertThat(entry.expectedSegments()).isEqualTo(2);
            assertThat(entry.actualSegments()).isEqualTo(1);
            assertThat(entry.delta()).isEqualTo(-1);
        });
        assertThat(captured.delta()).isEqualTo(-2L);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void stubClientBalance(long balance) {
        ClientCreditBalance clientBalance = ClientCreditBalance.builder()
                .clientId(CLIENT_ID)
                .balance(balance)
                .build();
        when(clientCreditBalanceRepository.findByClientIdForUpdate(CLIENT_ID))
                .thenReturn(Optional.of(clientBalance));
    }

    private void stubPlatformBalance(long balance) {
        PlatformCreditBalance platformBalance = PlatformCreditBalance.builder()
                .balance(balance)
                .build();
        when(platformCreditBalanceRepository.findForUpdate())
                .thenReturn(Optional.of(platformBalance));
    }
}
