package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.*;
import com.softropic.sendam.gateway.sms.repo.*;
import com.softropic.sendam.gateway.billing.service.CreditReservationService;
import com.softropic.sendam.gateway.billing.service.CreditService;
import com.softropic.sendam.gateway.billing.contract.BalanceResponse;
import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.security.service.RateLimitingService;
import com.softropic.sendam.gateway.account.contract.RateLimitExceededException;

import org.springframework.context.ApplicationEventPublisher;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock
    private SendRequestRepository sendRequestRepo;
    @Mock
    private SendRequestRecipientRepository recipientRepo;
    @Mock
    private CreditReservationService creditReservationService;
    @Mock
    private CreditService creditService;
    @Mock
    private RateLimitingService rateLimitingService;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SmsService smsService;

    private static final Long CLIENT_ID = 1L;
    private static final String SENDER = "SENDER";
    private static final String MESSAGE = "Hello World";
    private static final List<String> RECIPIENTS = List.of("671234567");

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerRegistry.circuitBreaker("nexah")).thenReturn(circuitBreaker);
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("sendSms: success - immediate send reserves credits and persists request")
    void sendSms_success_immediate() {
        SendSmsRequest request = new SendSmsRequest("req-123", MESSAGE, RECIPIENTS, null);

        lenient().when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.empty());
        lenient().when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong())).thenReturn(true);
        lenient().when(creditReservationService.reserve(eq(CLIENT_ID), anyLong(), anyString())).thenReturn(42L);
        lenient().when(sendRequestRepo.save(any())).thenAnswer(invocation -> {
            SendRequest r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        lenient().when(creditService.getBalance(CLIENT_ID)).thenReturn(new BalanceResponse(100L, "units", "XAF", Instant.now()));

        SendSmsResponse response = smsService.sendSms(CLIENT_ID, request);

        assertThat(response.sendRequestId()).isEqualTo("req-123");
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.requestId()).isEqualTo("req_1");

        // "Hello World" = 1 segment, 1 recipient → reservationAmount = (1+1)*1 = 2 (RESV-02)
        verify(creditReservationService).reserve(eq(CLIENT_ID), eq(2L), contains("req-123"));
        verify(sendRequestRepo).save(any(SendRequest.class));
        verify(recipientRepo).save(any(SendRequestRecipient.class));
    }

    @Test
    @DisplayName("sendSms: failure - recipient rate limit exceeded")
    void sendSms_recipientRateLimitExceeded() {
        SendSmsRequest request = new SendSmsRequest("req-123", MESSAGE, RECIPIENTS, null);

        lenient().when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.empty());
        lenient().when(rateLimitingService.tryConsume(anyString(), eq("sms_recipients"), anyLong(), anyLong(), any(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> smsService.sendSms(CLIENT_ID, request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(creditReservationService, never()).reserve(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("sendSms: failure - provider unavailable (CircuitBreaker OPEN)")
    void sendSms_providerUnavailable() {
        SendSmsRequest request = new SendSmsRequest("req-123", MESSAGE, RECIPIENTS, null);

        lenient().when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.empty());
        lenient().when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong())).thenReturn(true);
        lenient().when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> smsService.sendSms(CLIENT_ID, request))
                .isInstanceOf(ProviderUnavailableException.class);

        verify(creditReservationService, never()).reserve(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("sendSms: success - idempotency returns existing request")
    void sendSms_idempotency() {
        SendSmsRequest request = new SendSmsRequest("req-123", MESSAGE, RECIPIENTS, null);
        SendRequest existing = SendRequest.builder()
                .id(1L)
                .sendRequestId("req-123")
                .sendStatus(SendRequestStatus.ACCEPTED)
                .build();

        when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.of(existing));
        when(creditService.getBalance(CLIENT_ID)).thenReturn(new BalanceResponse(100L, "units", "XAF", Instant.now()));

        SendSmsResponse response = smsService.sendSms(CLIENT_ID, request);

        assertThat(response.sendRequestId()).isEqualTo("req-123");
        verify(creditReservationService, never()).reserve(anyLong(), anyLong(), anyString());
        verify(sendRequestRepo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // RESV-01, RESV-02, RESV-03: Buffer formula and raw/buffered amounts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("RESV-02: reservedCredits = (expectedSegments + 1) * recipientCount")
    void sendSms_reservedCredits_usesBufferedFormula() {
        // "Hello World" = 11 chars, GSM-7, single segment → expectedSegments = 1
        // 3 recipients → rawExpected = 1*3 = 3, reservation = (1+1)*3 = 6
        SendSmsRequest request = new SendSmsRequest(
                "req-resv-buf",
                "Hello World",
                List.of("671234567", "672345678", "673456789"),
                null);

        when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-resv-buf"))
                .thenReturn(Optional.empty());
        when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(true);
        when(creditReservationService.reserve(eq(CLIENT_ID), eq(6L), anyString()))
                .thenReturn(42L);
        when(sendRequestRepo.save(any())).thenAnswer(invocation -> {
            SendRequest r = invocation.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(creditService.getBalance(CLIENT_ID))
                .thenReturn(new BalanceResponse(94L, "units", "XAF", Instant.now()));

        ArgumentCaptor<SendRequest> reqCaptor = ArgumentCaptor.forClass(SendRequest.class);

        smsService.sendSms(CLIENT_ID, request);

        verify(sendRequestRepo).save(reqCaptor.capture());
        SendRequest saved = reqCaptor.getValue();
        assertThat(saved.getReservedCredits()).isEqualTo(6L);    // buffered: (1+1)*3
        assertThat(saved.getRawExpectedCredits()).isEqualTo(3L); // unbuffered: 1*3
        assertThat(saved.getSegmentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("RESV-03: rawExpectedCredits stored without buffer")
    void sendSms_rawExpectedCredits_storedUnbuffered() {
        // 2-segment message (161 chars GSM-7), 2 recipients
        // expectedSegments = 2, rawExpected = 4, reservation = (2+1)*2 = 6
        String twoSegmentMsg = "A".repeat(161); // 161 GSM-7 chars → 2 segments
        SendSmsRequest request = new SendSmsRequest(
                "req-resv-raw",
                twoSegmentMsg,
                List.of("671234567", "672345678"),
                null);

        when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-resv-raw"))
                .thenReturn(Optional.empty());
        when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(true);
        when(creditReservationService.reserve(eq(CLIENT_ID), eq(6L), anyString()))
                .thenReturn(43L);
        when(sendRequestRepo.save(any())).thenAnswer(invocation -> {
            SendRequest r = invocation.getArgument(0);
            r.setId(11L);
            return r;
        });
        when(creditService.getBalance(CLIENT_ID))
                .thenReturn(new BalanceResponse(94L, "units", "XAF", Instant.now()));

        ArgumentCaptor<SendRequest> reqCaptor = ArgumentCaptor.forClass(SendRequest.class);

        smsService.sendSms(CLIENT_ID, request);

        verify(sendRequestRepo).save(reqCaptor.capture());
        SendRequest saved = reqCaptor.getValue();
        assertThat(saved.getRawExpectedCredits()).isEqualTo(4L);  // 2 segments * 2 recipients
        assertThat(saved.getReservedCredits()).isEqualTo(6L);     // (2+1) * 2
    }

    @Test
    @DisplayName("RESV-04: expectedSegments stored per recipient")
    void sendSms_expectedSegments_storedOnEachRecipient() {
        // 1-segment message, 2 recipients → each recipient row gets expectedSegments=1
        SendSmsRequest request = new SendSmsRequest(
                "req-resv-perrecip",
                "Hello",
                List.of("671234567", "672345678"),
                null);

        when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-resv-perrecip"))
                .thenReturn(Optional.empty());
        when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(true);
        when(creditReservationService.reserve(eq(CLIENT_ID), eq(4L), anyString()))
                .thenReturn(44L);
        when(sendRequestRepo.save(any())).thenAnswer(invocation -> {
            SendRequest r = invocation.getArgument(0);
            r.setId(12L);
            return r;
        });
        when(creditService.getBalance(CLIENT_ID))
                .thenReturn(new BalanceResponse(96L, "units", "XAF", Instant.now()));

        ArgumentCaptor<SendRequestRecipient> recipCaptor =
                ArgumentCaptor.forClass(SendRequestRecipient.class);

        smsService.sendSms(CLIENT_ID, request);

        verify(recipientRepo, times(2)).save(recipCaptor.capture());
        recipCaptor.getAllValues().forEach(r ->
                assertThat(r.getExpectedSegments()).isEqualTo(1));
    }

    @Test
    @DisplayName("RESV-02 edge: single recipient, 1 segment → reservation = 2")
    void sendSms_singleRecipientSingleSegment_reservationIsTwo() {
        SendSmsRequest request = new SendSmsRequest(
                "req-resv-edge",
                "Hi",
                List.of("671234567"),
                null);

        when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-resv-edge"))
                .thenReturn(Optional.empty());
        when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(true);
        // reservation must be (1+1)*1 = 2
        when(creditReservationService.reserve(eq(CLIENT_ID), eq(2L), anyString()))
                .thenReturn(45L);
        when(sendRequestRepo.save(any())).thenAnswer(invocation -> {
            SendRequest r = invocation.getArgument(0);
            r.setId(13L);
            return r;
        });
        when(creditService.getBalance(CLIENT_ID))
                .thenReturn(new BalanceResponse(48L, "units", "XAF", Instant.now()));

        smsService.sendSms(CLIENT_ID, request);

        // If reserve() was called with 1 instead of 2, the strict stub would throw.
        // Reaching this line confirms the correct amount was passed.
        verify(creditReservationService).reserve(CLIENT_ID, 2L, "sms:req-resv-edge");
    }
}
