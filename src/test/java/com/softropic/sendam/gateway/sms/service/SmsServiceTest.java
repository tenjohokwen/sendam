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
        SendSmsRequest request = new SendSmsRequest("req-123", SENDER, MESSAGE, RECIPIENTS, null);

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

        verify(creditReservationService).reserve(eq(CLIENT_ID), eq(1L), contains("req-123"));
        verify(sendRequestRepo).save(any(SendRequest.class));
        verify(recipientRepo).save(any(SendRequestRecipient.class));
    }

    @Test
    @DisplayName("sendSms: failure - invalid sender ID throws SmsValidationException")
    void sendSms_invalidSenderId() {
        SendSmsRequest request = new SendSmsRequest("req-123", "INVALID_SENDER_ID_TOO_LONG", MESSAGE, RECIPIENTS, null);

        lenient().when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.empty());
        lenient().when(rateLimitingService.tryConsume(anyString(), anyString(), anyLong(), anyLong(), any(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> smsService.sendSms(CLIENT_ID, request))
                .isInstanceOf(SmsValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", SmsError.INVALID_SENDER_ID);

        verify(creditReservationService, never()).reserve(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("sendSms: failure - recipient rate limit exceeded")
    void sendSms_recipientRateLimitExceeded() {
        SendSmsRequest request = new SendSmsRequest("req-123", SENDER, MESSAGE, RECIPIENTS, null);

        lenient().when(sendRequestRepo.findByClientIdAndSendRequestId(CLIENT_ID, "req-123")).thenReturn(Optional.empty());
        lenient().when(rateLimitingService.tryConsume(anyString(), eq("sms_recipients"), anyLong(), anyLong(), any(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> smsService.sendSms(CLIENT_ID, request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(creditReservationService, never()).reserve(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("sendSms: failure - provider unavailable (CircuitBreaker OPEN)")
    void sendSms_providerUnavailable() {
        SendSmsRequest request = new SendSmsRequest("req-123", SENDER, MESSAGE, RECIPIENTS, null);

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
        SendSmsRequest request = new SendSmsRequest("req-123", SENDER, MESSAGE, RECIPIENTS, null);
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
}
