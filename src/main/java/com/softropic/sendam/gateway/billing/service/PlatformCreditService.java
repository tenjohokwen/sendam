package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.InsufficientPlatformBalanceException;
import com.softropic.sendam.gateway.billing.contract.PlatformBalanceResponse;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryDto;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerHistoryResponse;
import com.softropic.sendam.gateway.billing.contract.RecordNexahPurchaseRequest;
import com.softropic.sendam.gateway.billing.contract.RecordNexahPurchaseResponse;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalance;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditLedgerEntry;
import com.softropic.sendam.gateway.billing.repo.PlatformCreditLedgerRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PlatformCreditService {

    private final PlatformCreditBalanceRepository balanceRepository;
    private final PlatformCreditLedgerRepository ledgerRepository;

    /**
     * Core write method: acquires pessimistic lock on the singleton balance row, appends a ledger
     * entry, and updates the running balance atomically.
     *
     * <p>Called by {@code recordNexahPurchase} (this service) and by TopupService (Plan 03/04)
     * when approving a client topup that should debit the platform balance.
     *
     * <p>Lock order: (1) topup row [caller], (2) client credit balance [CreditService],
     * (3) platform balance [here] — never invert this order.
     *
     * @param type      the type of platform ledger entry
     * @param amount    signed amount: positive = credit, negative = debit
     * @param reference optional reference (provider order id, topup id, etc.)
     * @throws InsufficientPlatformBalanceException if the resulting balance would be negative
     */
    public void applyLedgerEntry(PlatformLedgerEntryType type, long amount, String reference) {
        // Lock order: (1) topup row [caller], (2) client credit balance [CreditService], (3) platform balance [here] — never invert this order
        PlatformCreditBalance lockRow = balanceRepository.findForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform balance not initialized",
                        "platform_credit_balance"));

        long newBalance = lockRow.getBalance() + amount;

        if (newBalance < 0) {
            throw new InsufficientPlatformBalanceException(
                    "Platform balance insufficient for operation",
                    lockRow.getBalance(),
                    Math.abs(amount));
        }

        PlatformCreditLedgerEntry entry = PlatformCreditLedgerEntry.builder()
                .entryType(type)
                .amount(amount)
                .balanceAfter(newBalance)
                .reference(reference)
                .status(EntityStatus.ACTIVE)
                .build();
        ledgerRepository.save(entry);

        lockRow.setBalance(newBalance);
        balanceRepository.save(lockRow);

        log.info("Platform ledger entry written: type={}, amount={}, newBalance={}", type, amount, newBalance);
    }

    /**
     * Records a Nexah credit purchase: increases the platform balance by the given amount.
     * Used by the Admin API when the operator purchases SMS credits from Nexah.
     *
     * @param request validated request with positive amount and optional reference
     * @return updated balance plus echo of the recorded amount and timestamp
     */
    public RecordNexahPurchaseResponse recordNexahPurchase(RecordNexahPurchaseRequest request) {
        String reference = request.reference() != null ? request.reference() : "nexah_purchase";
        applyLedgerEntry(PlatformLedgerEntryType.NEXAH_PURCHASE, request.amount(), reference);

        PlatformCreditBalance updated = balanceRepository.findBalance()
                .orElseThrow();

        log.info("Nexah purchase recorded: amount={}, newBalance={}", request.amount(), updated.getBalance());

        return new RecordNexahPurchaseResponse(updated.getBalance(), request.amount(), Instant.now());
    }

    /**
     * Returns the current platform balance without acquiring a lock.
     * Safe for read-only display; not suitable for deciding whether to proceed with a debit.
     */
    @Transactional(readOnly = true)
    public PlatformBalanceResponse getBalance() {
        PlatformCreditBalance row = balanceRepository.findBalance()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform balance not initialized",
                        "platform_credit_balance"));
        return new PlatformBalanceResponse(row.getBalance(), row.getLastModifiedDate());
    }

    /**
     * Returns paginated platform ledger history, optionally filtered by entry type.
     * Page size is capped at 200 to prevent unbounded result sets.
     *
     * @param type nullable — when null all entries are returned
     * @param page 0-based page index
     * @param size requested page size (capped at 200)
     */
    @Transactional(readOnly = true)
    public PlatformLedgerHistoryResponse getLedgerHistory(PlatformLedgerEntryType type, int page, int size) {
        int effectiveSize = Math.min(size, 200);
        Pageable pageable = PageRequest.of(page, effectiveSize);
        Page<PlatformCreditLedgerEntry> pageResult = ledgerRepository.findByOptionalType(type, pageable);

        List<PlatformLedgerEntryDto> dtos = pageResult.getContent().stream()
                .map(e -> new PlatformLedgerEntryDto(
                        e.getCreatedDate(),
                        e.getEntryType(),
                        e.getAmount(),
                        e.getBalanceAfter(),
                        e.getReference()))
                .toList();

        return new PlatformLedgerHistoryResponse(dtos, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }
}
