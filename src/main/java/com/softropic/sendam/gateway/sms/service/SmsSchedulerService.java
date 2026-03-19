package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for SMS requests due for dispatch and submits them to a provider via SmsSender.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsSchedulerService {

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;
    private final List<SmsSender> smsSenders; // Autowires all SmsSender implementations
    private final ApplicationEventPublisher eventPublisher;
    private final SmsDispatchWorker dispatchWorker;
    private final SmsRecoveryWorker recoveryWorker;
    private final TransactionTemplate transactionTemplate;

    private static final int BATCH_SIZE = 50;
    private static final int RECOVERY_BATCH_SIZE = 50;


    @Scheduled(fixedDelay = 30_000)
    public void dispatchScheduledMessages() {
        if (smsSenders.isEmpty()) {
            log.error("No SmsSender implementations found! Cannot dispatch messages.");
            return;
        }

        // For v1, we just use the first available sender (Nexah)
        SmsSender sender = smsSenders.get(0);
        Instant now = Instant.now();

        log.info("Starting dispatch cycle using {}", sender.getClass().getSimpleName());

        int totalProcessed = 0;
        while (true) {
            // Step 1: Grab a batch of ACCEPTED requests and mark them as SENDING (Transaction 1)
            List<SendRequest> toDispatch = dispatchWorker.grabBatch(now, BATCH_SIZE);
            if (toDispatch.isEmpty()) {
                break;
            }

            log.info("Processing batch of {} SMS requests", toDispatch.size());

            // Step 2: Process the batch outside of any DB transaction holding locks
            for (SendRequest request : toDispatch) {
                List<SendRequestRecipient> recipients = List.of();
                try {
                    recipients = dispatchWorker.getRecipients(request.getId());
                    
                    sender.send(request, recipients);

                    // Step 3a: Save recipient-level results (IDs and SUBMITTED status)
                    dispatchWorker.saveRecipientStatuses(recipients);

                    // Step 3b: Transition parent to SUBMITTED
                    dispatchWorker.markParentAsSubmitted(request);
                    
                    log.info("Dispatched sendRequestId={} — {} recipient(s) transitioned to SUBMITTED",
                            request.getSendRequestId(), recipients.size());

                } catch (ProviderUnavailableException e) {
                    log.warn("Provider unavailable for sendRequestId={}. Reverting to ACCEPTED for next cycle.",
                            request.getSendRequestId());
                    dispatchWorker.revertToAccepted(request);
                } catch (Exception e) {
                    // Critical: if sender.send succeeded but DB save fails, we have a blind spot.
                    // We log heavily to ensure a trace exists.
                    String recipientData = "";
                    if(CollectionUtils.isNotEmpty(recipients)) {
                        recipientData = recipients.stream()
                                                   .map(SendRequestRecipient::toString)
                                                   .reduce((s1, s2) -> s1 + ", " + s2)
                                                   .orElse("");
                    }
                    log.atError()
                       .addKeyValue("TAG", "INVESTIGATE")
                       .addKeyValue("requestId", request.getSendRequestId())
                       .addKeyValue("recipientData", recipientData)
                       .setCause(e)
                       .setMessage("Failed to persist submission results for sendsms request to provider! Possible double-send if retried.");
                    dispatchWorker.handleError(request, e);
                }
            }

            totalProcessed += toDispatch.size();
            // Optional: Limit total processed per cycle to prevent infinite runs
            if (totalProcessed >= 1000) {
                log.warn("Dispatch cycle reached max limit of 1000. Will resume in next scheduled run.");
                break;
            }
        }

        if (totalProcessed > 0) {
            log.info("Finished dispatch cycle. Total processed: {}", totalProcessed);
        }
    }


    /**
     * Handles requests stuck in SENDING (10m+).
     * Runs every 5 minutes to ensure rapid recovery from node failures.
     */
    @Scheduled(fixedDelay = 300_000)
    public void recoverStuckSending() {
        log.info("Starting stuck sending SMS recovery cycle");
        Instant sendCutoff = Instant.now().minusSeconds(600); // 10 minutes
        int totalProcessed = 0;
        while (true) {
            Integer batchCount = transactionTemplate.execute(status -> {
                List<SendRequest> batch = recoveryWorker.grabStuckSendingBatch(sendCutoff, RECOVERY_BATCH_SIZE);
                if (batch.isEmpty()) {
                    return 0;
                }
                for (SendRequest request : batch) {
                    recoveryWorker.recoverStuckSending(request);
                }
                return batch.size();
            });

            if (batchCount == null || batchCount == 0) break;
            totalProcessed += batchCount;
            if (batchCount < RECOVERY_BATCH_SIZE) break;
        }
        if (totalProcessed > 0) log.warn("Recovered {} stuck SENDING request(s)", totalProcessed);
        log.info("Finished stuck sending SMS recovery cycle");
    }

    /**
     * Force-finalizes requests stuck in SUBMITTED (24h+).
     * Runs every 12 hours to ensure rapid recovery from node failures.
     */
    @Scheduled(fixedDelay = 43_200_000)
    public void recoverStaleSubmitted() {
        log.info("Starting stale SMS recovery cycle");
        Instant subCutoff = Instant.now().minusSeconds(86_400); // 24 hours
        int totalProcessed = 0;
        while (true) {
            Integer batchCount = transactionTemplate.execute(status -> {
                List<SendRequest> batch = recoveryWorker.grabStaleSubmittedBatch(subCutoff, RECOVERY_BATCH_SIZE);
                if (batch.isEmpty()) {
                    return 0;
                }
                for (SendRequest request : batch) {
                    recoveryWorker.forceFinalize(request);
                }
                return batch.size();
            });

            if (batchCount == null || batchCount == 0) break;
            totalProcessed += batchCount;
            if (batchCount < RECOVERY_BATCH_SIZE) break;
        }
        if (totalProcessed > 0) log.warn("Force-finalized {} stale SUBMITTED request(s)", totalProcessed);
        log.info("Finished stale SMS recovery cycle");
    }
}
