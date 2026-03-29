package com.softropic.sendam.email.infrastructure;

import com.softropic.sendam.email.contract.EmailDeliveryStatus;
import com.softropic.sendam.email.contract.Envelope;
import com.softropic.sendam.email.repo.EnvelopeEntity;
import com.softropic.sendam.email.repo.EnvelopeEntityRepository;
import com.softropic.sendam.email.service.EnvelopeMapper;
import com.softropic.sendam.email.service.MailManager;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job that retries email deliveries that previously failed with a retryable error.
 *
 * <p>Each invocation issues a {@code SELECT FOR UPDATE SKIP LOCKED} query so that concurrent
 * application instances each claim a disjoint batch of rows and never race over the same
 * envelope. The transaction used for fetching and marking rows as {@link EmailDeliveryStatus#SENDING}
 * is kept short to avoid holding DB locks while communicating with external SMTP servers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final EnvelopeEntityRepository envelopeEntityRepository;
    private final MailManager mailManager;
    private final TransactionTemplate transactionTemplate;

    /**
     * Maximum number of scheduler-level retries permitted per envelope.
     * When {@code attempts} reaches this value the envelope is marked
     * {@link EmailDeliveryStatus#ATTEMPTS_EXHAUSTED} and no further send is attempted.
     *
     * <p>Interpretation A: the initial send (via the event listener) is not a retry.
     * {@code attempts} is incremented once per {@code sendEmailSync} call, so a value of 6
     * means 1 initial + 5 scheduler retries have already occurred.
     */
    static final long MAX_RETRY_ATTEMPTS = 6;

    private record RetryCandidate(Envelope envelope, long attempts, UUID entityId) {}

    /**
     * Polls for failed, retryable emails and re-attempts delivery.
     *
     * <p>Runs with a fixed delay (default 60 s, overridable via {@code email.retry.interval-ms})
     * so a slow batch always completes before the next one starts.
     *
     * <p>The {@code SELECT FOR UPDATE SKIP LOCKED} in {@link EnvelopeEntityRepository#fetchFailedEmails()}
     * ensures that in a multi-instance deployment each pod works on a distinct set of rows.
     *
     * <p>Before each send attempt the following pre-checks are applied in order:
     * <ol>
     *   <li>If the envelope's deadline has passed it is marked
     *       {@link EmailDeliveryStatus#DEADLINE_EXPIRED} and skipped.</li>
     *   <li>If {@code attempts >= MAX_RETRY_ATTEMPTS} it is marked
     *       {@link EmailDeliveryStatus#ATTEMPTS_EXHAUSTED} and skipped.</li>
     * </ol>
     * In both cases {@code retry} is set to {@code false} so the row is never fetched again.
     */
    @Scheduled(fixedDelayString = "${email.retry.interval-ms:60000}")
    public void retryFailedEmails() {
        List<RetryCandidate> candidates = transactionTemplate.execute(status -> {
            List<EnvelopeEntity> entities = envelopeEntityRepository.fetchFailedEmails();
            List<RetryCandidate> result = new ArrayList<>();
            for (EnvelopeEntity entity : entities) {
                entity.setStatus(EmailDeliveryStatus.SENDING);
                result.add(new RetryCandidate(EnvelopeMapper.toEnvelope(entity), entity.getAttempts(), entity.getId()));
            }
            return result;
        });

        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        log.info("Retrying {} failed email(s)", candidates.size());
        for (RetryCandidate candidate : candidates) {
            Envelope envelope = candidate.envelope();
            if (Instant.now().isAfter(envelope.deadline())) {
                log.warn("Deadline expired for sendId='{}', marking as DEADLINE_EXPIRED", envelope.sendId());
                updateStatus(candidate.entityId(), EmailDeliveryStatus.DEADLINE_EXPIRED, false);
                continue;
            }
            if (candidate.attempts() >= MAX_RETRY_ATTEMPTS) {
                log.warn("Attempts exhausted for sendId='{}' (attempts={}), marking as ATTEMPTS_EXHAUSTED",
                         envelope.sendId(), candidate.attempts());
                updateStatus(candidate.entityId(), EmailDeliveryStatus.ATTEMPTS_EXHAUSTED, false);
                continue;
            }
            try {
                mailManager.sendEmailSync(envelope);
            } catch (Exception e) {
                log.error("Retry attempt failed for sendId='{}': {}", envelope.sendId(), e.getMessage());
            }
        }
    }

    private void updateStatus(UUID entityId, EmailDeliveryStatus status, boolean retry) {
        transactionTemplate.executeWithoutResult(s -> {
            envelopeEntityRepository.findById(entityId).ifPresent(entity -> {
                entity.setStatus(status);
                entity.setRetry(retry);
                envelopeEntityRepository.save(entity);
            });
        });
    }
}
