package com.softropic.sendam.client.service;

import com.softropic.sendam.client.repo.SendRequestRecipientRepository;
import com.softropic.sendam.client.repo.SendRequestRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly purge job that removes finalized SMS send requests older than 30 days.
 *
 * <p>Only {@code FINALIZED} and {@code FAIL_FINALIZED} records are targeted — active,
 * scheduled, or submitted messages are never touched.
 *
 * <p>Deletion is child-first (send_request_recipient) then parent (send_request) to
 * satisfy the FK constraint. Batches of 500 prevent unbounded IN-clause lengths.
 *
 * <p>{@link org.springframework.scheduling.annotation.EnableScheduling} is declared on
 * {@link com.softropic.sendam.client.config.ClientConfig} — no additional annotation needed here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsPurgeService {

    private static final int BATCH_SIZE = 500;

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;

    /**
     * Purges send_request rows (and their recipients) whose {@code finalized_at} timestamp
     * is older than 30 days. Runs daily at 02:00 server time.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldMessages() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Long> ids = sendRequestRepository.findFinalizedBefore(cutoff);
        if (ids.isEmpty()) {
            log.debug("No messages to purge");
            return;
        }

        // Process in batches to avoid unbounded IN-clause lengths
        int total = ids.size();
        for (int i = 0; i < total; i += BATCH_SIZE) {
            List<Long> batch = ids.subList(i, Math.min(i + BATCH_SIZE, total));
            // Delete children before parents to satisfy FK constraint
            recipientRepository.deleteBySendRequestIdFkIn(batch);
            sendRequestRepository.deleteAllByIdIn(batch);
        }

        log.info("Purged {} send_request records (finalized_at < {})", total, cutoff);
    }
}
