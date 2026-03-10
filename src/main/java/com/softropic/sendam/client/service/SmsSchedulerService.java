package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.SendRequestStatus;
import com.softropic.sendam.client.repo.SendRequest;
import com.softropic.sendam.client.repo.SendRequestRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for scheduled SMS requests that are due for dispatch.
 *
 * <p>Runs every 30 seconds (fixedDelay — not fixedRate — so runs never overlap).
 * In Phase 3, due requests are transitioned to SUBMITTED status.
 * Phase 4 will replace this stub with an actual Nexah HTTP call.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsSchedulerService {

    private final SendRequestRepository sendRequestRepository;

    /**
     * Finds all ACCEPTED scheduled requests with scheduleTime <= now and marks them SUBMITTED.
     * Uses fixedDelay so the next run starts 30s after the previous run completes,
     * preventing overlapping executions under heavy load.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchScheduledMessages() {
        Instant now = Instant.now();
        List<SendRequest> due = sendRequestRepository.findDueScheduledRequests(now);
        if (due.isEmpty()) {
            return; // skip log noise on empty polls
        }
        log.info("Dispatching {} due scheduled SMS request(s)", due.size());
        for (SendRequest request : due) {
            try {
                // Phase 3: mark SUBMITTED. Phase 4 replaces this with actual Nexah HTTP call.
                request.setSendStatus(SendRequestStatus.SUBMITTED);
                sendRequestRepository.save(request);
                log.info("Scheduled request {} marked SUBMITTED", request.getSendRequestId());
            } catch (Exception e) {
                log.error("Failed to dispatch scheduled request {}", request.getSendRequestId(), e);
                // Continue processing remaining requests — one failure must not block others
            }
        }
    }
}
