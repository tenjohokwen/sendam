package com.softropic.sendam.email.infrastructure;

import com.softropic.sendam.email.contract.EmailDeliveryStatus;
import com.softropic.sendam.email.contract.EmailTemplate;
import com.softropic.sendam.email.contract.Envelope;
import com.softropic.sendam.email.repo.EnvelopeEntity;
import com.softropic.sendam.email.repo.EnvelopeEntityRepository;
import com.softropic.sendam.email.repo.RecipientEntity;
import com.softropic.sendam.email.service.MailManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailRetryScheduler}.
 *
 * Covers scheduling logic in isolation: pre-checks (deadline, attempt limit) and
 * per-row failure isolation. {@link EnvelopeEntityRepository} and {@link MailManager}
 * are mocked; {@link com.softropic.sendam.email.service.EnvelopeMapper} runs as production code.
 */
@ExtendWith(MockitoExtension.class)
class EmailRetrySchedulerTest {

    @Mock
    private EnvelopeEntityRepository envelopeEntityRepository;

    @Mock
    private MailManager mailManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EmailRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Mock transactionTemplate to just execute the callback
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            ((Consumer<Object>) callback).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // -----------------------------------------------------------------
    // empty batch
    // -----------------------------------------------------------------

    @Test
    void retryFailedEmails_doesNothingWhenNoCandidates() {
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of());

        scheduler.retryFailedEmails();

        verifyNoInteractions(mailManager);
    }

    // -----------------------------------------------------------------
    // normal retry path
    // -----------------------------------------------------------------

    @Test
    void retryFailedEmails_callsSendEmailSyncForEachEligibleCandidate() {
        String sendId1 = UUID.randomUUID().toString();
        String sendId2 = UUID.randomUUID().toString();
        when(envelopeEntityRepository.fetchFailedEmails())
                .thenReturn(List.of(buildEntity(sendId1, 1, Instant.now().plus(Duration.ofDays(1))),
                                    buildEntity(sendId2, 1, Instant.now().plus(Duration.ofDays(1)))));

        scheduler.retryFailedEmails();

        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(mailManager, times(2)).sendEmailSync(captor.capture());
        assertThat(captor.getAllValues().stream().map(Envelope::sendId).toList())
                .containsExactlyInAnyOrder(sendId1, sendId2);
    }

    @Test
    void retryFailedEmails_envelopeDataIsPreservedFromEntity() {
        String sendId = UUID.randomUUID().toString();
        when(envelopeEntityRepository.fetchFailedEmails())
                .thenReturn(List.of(buildEntity(sendId, 1, Instant.now().plus(Duration.ofDays(1)))));

        scheduler.retryFailedEmails();

        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(mailManager).sendEmailSync(captor.capture());
        Envelope sent = captor.getValue();
        assertThat(sent.sendId()).isEqualTo(sendId);
        assertThat(sent.emailTemplate()).isEqualTo(EmailTemplate.ACTIVATION);
        assertThat(sent.data()).containsEntry("activationKey", "abc123");
        assertThat(sent.recipients()).hasSize(1);
        assertThat(sent.recipients().get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void retryFailedEmails_continuesProcessingAfterSingleEntityFailure() {
        String failingSendId    = UUID.randomUUID().toString();
        String succeedingSendId = UUID.randomUUID().toString();
        when(envelopeEntityRepository.fetchFailedEmails())
                .thenReturn(List.of(buildEntity(failingSendId,    1, Instant.now().plus(Duration.ofDays(1))),
                                    buildEntity(succeedingSendId, 1, Instant.now().plus(Duration.ofDays(1)))));

        doThrow(new RuntimeException("SMTP down"))
                .when(mailManager).sendEmailSync(argThat(e -> failingSendId.equals(e.sendId())));

        scheduler.retryFailedEmails();

        verify(mailManager, times(2)).sendEmailSync(any(Envelope.class));
    }

    // -----------------------------------------------------------------
    // DEADLINE_EXPIRED
    // -----------------------------------------------------------------

    @Test
    void retryFailedEmails_marksDeadlineExpiredAndSkipsSend() {
        String sendId = UUID.randomUUID().toString();
        EnvelopeEntity expired = buildEntity(sendId, 1, Instant.now().minus(Duration.ofSeconds(1)));
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of(expired));
        when(envelopeEntityRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

        scheduler.retryFailedEmails();

        verify(mailManager, never()).sendEmailSync(any());
        assertThat(expired.getStatus()).isEqualTo(EmailDeliveryStatus.DEADLINE_EXPIRED);
        assertThat(expired.isRetry()).isFalse();
    }

    @Test
    void retryFailedEmails_deadlineTakesPriorityWhenBothConditionsAreMet() {
        // attempts >= MAX and deadline past — deadline check must fire first
        String sendId = UUID.randomUUID().toString();
        EnvelopeEntity entity = buildEntity(sendId, (int) EmailRetryScheduler.MAX_RETRY_ATTEMPTS,
                                            Instant.now().minus(Duration.ofSeconds(1)));
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of(entity));
        when(envelopeEntityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        scheduler.retryFailedEmails();

        verify(mailManager, never()).sendEmailSync(any());
        assertThat(entity.getStatus()).isEqualTo(EmailDeliveryStatus.DEADLINE_EXPIRED);
    }

    // -----------------------------------------------------------------
    // ATTEMPTS_EXHAUSTED
    // -----------------------------------------------------------------

    @Test
    void retryFailedEmails_marksAttemptsExhaustedAtThresholdAndSkipsSend() {
        String sendId = UUID.randomUUID().toString();
        EnvelopeEntity exhausted = buildEntity(sendId, (int) EmailRetryScheduler.MAX_RETRY_ATTEMPTS,
                                               Instant.now().plus(Duration.ofDays(1)));
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of(exhausted));
        when(envelopeEntityRepository.findById(exhausted.getId())).thenReturn(Optional.of(exhausted));

        scheduler.retryFailedEmails();

        verify(mailManager, never()).sendEmailSync(any());
        assertThat(exhausted.getStatus()).isEqualTo(EmailDeliveryStatus.ATTEMPTS_EXHAUSTED);
        assertThat(exhausted.isRetry()).isFalse();
    }

    @Test
    void retryFailedEmails_doesNotExhaustWhenAttemptsOneBelowThreshold() {
        String sendId = UUID.randomUUID().toString();
        EnvelopeEntity entity = buildEntity(sendId, (int) (EmailRetryScheduler.MAX_RETRY_ATTEMPTS - 1),
                                            Instant.now().plus(Duration.ofDays(1)));
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of(entity));

        scheduler.retryFailedEmails();

        verify(mailManager).sendEmailSync(any());
        assertThat(entity.getStatus()).isNotEqualTo(EmailDeliveryStatus.ATTEMPTS_EXHAUSTED);
    }

    @Test
    void retryFailedEmails_marksAttemptsExhaustedAboveThreshold() {
        String sendId = UUID.randomUUID().toString();
        EnvelopeEntity entity = buildEntity(sendId, (int) EmailRetryScheduler.MAX_RETRY_ATTEMPTS + 2,
                                            Instant.now().plus(Duration.ofDays(1)));
        when(envelopeEntityRepository.fetchFailedEmails()).thenReturn(List.of(entity));
        when(envelopeEntityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        scheduler.retryFailedEmails();

        verify(mailManager, never()).sendEmailSync(any());
        assertThat(entity.getStatus()).isEqualTo(EmailDeliveryStatus.ATTEMPTS_EXHAUSTED);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private EnvelopeEntity buildEntity(String sendId, int attempts, Instant deadline) {
        RecipientEntity recipient = new RecipientEntity();
        recipient.setEmail("user@example.com");
        recipient.setLangKey("en");

        EnvelopeEntity entity = new EnvelopeEntity();
        entity.setId(UUID.randomUUID());
        entity.setSendId(sendId);
        entity.setEmailTemplate(EmailTemplate.ACTIVATION);
        entity.setDeadline(deadline);
        entity.setData(Map.of("activationKey", "abc123"));
        entity.setRecipients(List.of(recipient));
        entity.setStatus(EmailDeliveryStatus.FAILED);
        entity.setRetry(true);
        entity.setAttempts(attempts);
        return entity;
    }
}
