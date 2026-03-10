package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.exception.ProviderUnavailableException;
import com.softropic.sendam.client.repo.SendRequest;
import com.softropic.sendam.client.repo.SendRequestRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for SMS requests due for dispatch and submits them to Nexah via NexahDispatchService.
 *
 * <p>dispatchScheduledMessages() runs every 30 seconds (fixedDelay — not fixedRate — so
 * runs never overlap). It handles two categories of requests:
 * <ul>
 *   <li>Immediate sends: ACCEPTED with scheduleTime IS NULL
 *   <li>Due scheduled sends: ACCEPTED with scheduleTime IS NOT NULL AND scheduleTime <= now
 * </ul>
 *
 * <p>recoverStaleSms() runs every hour and force-finalizes requests that have been in SUBMITTED
 * state for 24+ hours without receiving a delivery report callback from Nexah.
 *
 * <p>Per-request exceptions are caught and logged — one failed dispatch must not block
 * the remaining requests in the same scheduler run.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsSchedulerService {

    private final SendRequestRepository sendRequestRepository;
    private final NexahDispatchService nexahDispatchService;
    private final DrCallbackService drCallbackService;

    /**
     * Dispatches both due scheduled requests and pending immediate requests via NexahDispatchService.
     * Uses fixedDelay so the next run starts 30s after the previous run completes,
     * preventing overlapping executions under heavy load.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchScheduledMessages() {
        Instant now = Instant.now();

        List<SendRequest> due = sendRequestRepository.findDueScheduledRequests(now);
        List<SendRequest> immediate = sendRequestRepository.findPendingImmediateRequests();

        List<SendRequest> toDispatch = new ArrayList<>();
        toDispatch.addAll(due);
        toDispatch.addAll(immediate);

        if (toDispatch.isEmpty()) {
            return; // skip log noise on empty polls
        }

        log.info("Dispatching {} SMS request(s) ({} scheduled-due, {} immediate)",
                toDispatch.size(), due.size(), immediate.size());

        for (SendRequest request : toDispatch) {
            try {
                nexahDispatchService.dispatch(request);
            } catch (ProviderUnavailableException e) {
                log.warn("Provider unavailable for sendRequestId={}. Will retry on next cycle.",
                        request.getSendRequestId());
                // Continue processing remaining requests — provider outage must not block others
            } catch (Exception e) {
                log.error("Failed to dispatch sendRequestId={}", request.getSendRequestId(), e);
                // Continue processing remaining requests — one failure must not block others
            }
        }
    }

    /**
     * Force-finalizes requests that have been stuck in SUBMITTED state for 24+ hours.
     * Runs every hour (fixedDelay — no overlap).
     *
     * <p>A request is considered stale when it has at least one recipient still in SUBMITTED
     * state and the send_request row's lastModifiedDate is older than 24 hours. This covers
     * cases where the Nexah DR callback was never received (network issues, provider errors).
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void recoverStaleSms() {
        Instant cutoff = Instant.now().minusSeconds(86_400); // 24 hours ago
        List<SendRequest> stale = sendRequestRepository.findStaleSubmittedRequests(cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.warn("Stale SMS recovery: force-finalizing {} request(s) stuck in SUBMITTED for 24+ hours", stale.size());

        for (SendRequest request : stale) {
            try {
                drCallbackService.forceFinalizeStaleSms(request.getId());
            } catch (Exception e) {
                log.error("Failed to force-finalize stale sendRequestId={}", request.getSendRequestId(), e);
            }
        }
    }
}
