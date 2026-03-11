package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.CreateTopupRequest;
import com.softropic.sendam.gateway.billing.contract.CreateTopupResponse;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.TopupStatus;
import com.softropic.sendam.gateway.billing.contract.TopupStatusResponse;
import com.softropic.sendam.gateway.billing.contract.DuplicateTransactionIdException;
import com.softropic.sendam.gateway.billing.contract.TopupAlreadyProcessedException;
import com.softropic.sendam.gateway.billing.repo.TopupRequestEntity;
import com.softropic.sendam.gateway.billing.repo.TopupRequestRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the top-up request lifecycle: submit (PENDING_APPROVAL), approve (APPROVED), reject (REJECTED).
 *
 * <p>All credit mutations route through CreditService.applyLedgerEntry() — TopupService never writes
 * directly to ledger or balance tables.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TopupService {

    private final TopupRequestRepository topupRepository;
    private final CreditService creditService;

    // ---- helpers ----

    private String formatTopupId(Long id) {
        return "top_" + id;
    }

    private Long parseTopupId(String topupId) {
        if (topupId == null || !topupId.startsWith("top_")) {
            throw new ResourceNotFoundException("topup_id not found", "topup_request");
        }
        try {
            return Long.parseLong(topupId.substring(4));
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("topup_id not found", "topup_request");
        }
    }

    // ---- public API ----

    /**
     * Creates a new top-up request for the given client.
     *
     * <p>Writes a TOPUP_PENDING ledger entry (amount=0, audit record only) after persisting the
     * request. The balance is NOT modified here; it changes only when the admin approves.
     *
     * @throws DuplicateTransactionIdException if (clientId, transaction_id) already exists
     */
    public CreateTopupResponse createTopup(Long clientId, CreateTopupRequest request) {
        TopupRequestEntity entity = TopupRequestEntity.builder()
                .clientId(clientId)
                .amount(request.amount())
                .transactionId(request.transactionId())
                .paymentType(request.paymentType())
                .accountNumber(request.accountNumber())
                .topupStatus(TopupStatus.PENDING_APPROVAL)
                .status(EntityStatus.ACTIVE)
                .build();

        TopupRequestEntity saved;
        try {
            saved = topupRepository.save(entity);
            topupRepository.flush(); // flush to trigger DB constraint check before leaving try block
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTransactionIdException("Duplicate transaction_id for this client");
        }

        // Informational ledger entry: amount=0 — does NOT change the balance
        creditService.applyLedgerEntry(clientId, LedgerEntryType.TOPUP_PENDING, 0L, formatTopupId(saved.getId()));

        log.info("Top-up created: topup_id={}, clientId={}, amount={}", formatTopupId(saved.getId()), clientId, request.amount());
        return new CreateTopupResponse(formatTopupId(saved.getId()), TopupStatus.PENDING_APPROVAL, saved.getCreatedDate());
    }

    /**
     * Returns the current status of a top-up request. Enforces client isolation —
     * a client can only query their own top-up requests.
     */
    @Transactional(readOnly = true)
    public TopupStatusResponse getTopupStatus(Long clientId, String topupId) {
        Long id = parseTopupId(topupId);
        TopupRequestEntity entity = topupRepository.findByClientIdAndId(clientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("topup_id not found", "topup_request"));
        return new TopupStatusResponse(topupId, entity.getAmount(), entity.getTopupStatus(), entity.getApprovedAt());
    }

    /**
     * Admin: approves a PENDING_APPROVAL top-up. Acquires a pessimistic lock on the top-up row
     * to prevent double-approval under concurrent admin requests. Credits the client's balance
     * via TOPUP_APPROVED ledger entry.
     *
     * @throws ResourceNotFoundException      if topup_id does not exist
     * @throws TopupAlreadyProcessedException if the top-up is not PENDING_APPROVAL
     */
    public TopupStatusResponse approve(String topupId) {
        Long id = parseTopupId(topupId);
        TopupRequestEntity entity = topupRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("topup_id not found", "topup_request"));

        if (entity.getTopupStatus() != TopupStatus.PENDING_APPROVAL) {
            throw new TopupAlreadyProcessedException("Top-up is already " + entity.getTopupStatus());
        }

        // Credit the balance — positive amount
        creditService.applyLedgerEntry(entity.getClientId(), LedgerEntryType.TOPUP_APPROVED, entity.getAmount(), topupId);

        entity.setTopupStatus(TopupStatus.APPROVED);
        entity.setApprovedAt(Instant.now());
        topupRepository.save(entity);

        log.info("Top-up approved: topup_id={}, clientId={}, amount={}", topupId, entity.getClientId(), entity.getAmount());
        return new TopupStatusResponse(topupId, entity.getAmount(), TopupStatus.APPROVED, entity.getApprovedAt());
    }

    /**
     * Admin: rejects a PENDING_APPROVAL top-up. No ledger entry is written — rejection does not
     * touch the balance.
     *
     * @throws ResourceNotFoundException      if topup_id does not exist
     * @throws TopupAlreadyProcessedException if the top-up is not PENDING_APPROVAL
     */
    public TopupStatusResponse reject(String topupId) {
        Long id = parseTopupId(topupId);
        TopupRequestEntity entity = topupRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("topup_id not found", "topup_request"));

        if (entity.getTopupStatus() != TopupStatus.PENDING_APPROVAL) {
            throw new TopupAlreadyProcessedException("Top-up is already " + entity.getTopupStatus());
        }

        // No ledger entry for rejection — balance unchanged
        entity.setTopupStatus(TopupStatus.REJECTED);
        entity.setRejectedAt(Instant.now());
        topupRepository.save(entity);

        log.info("Top-up rejected: topup_id={}, clientId={}", topupId, entity.getClientId());
        return new TopupStatusResponse(topupId, entity.getAmount(), TopupStatus.REJECTED, null);
    }
}
