package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.analytics.contract.ClientCreditConsumptionResponse;
import com.softropic.sendam.gateway.billing.contract.BalanceResponse;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryDto;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.LedgerHistoryResponse;
import com.softropic.sendam.gateway.billing.contract.SpendSummaryResponse;
import com.softropic.sendam.gateway.billing.contract.SpendSummaryRow;
import com.softropic.sendam.gateway.billing.contract.InsufficientBalanceException;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerEntry;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerRepository;
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
public class CreditService {

    private final ClientCreditBalanceRepository balanceRepository;
    private final CreditLedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public SpendSummaryResponse getSpendSummary(Long clientId, Instant from, Instant to) {
        SpendSummaryRow row = ledgerRepository.findSpendSummary(clientId, from, to);
        return new SpendSummaryResponse(
            row.getSmsDebit(),
            row.getSmsRefund(),
            row.getTopupApproved(),
            row.getSmsReservation(),
            row.getNetCreditsConsumed()
        );
    }

    @Transactional(readOnly = true)
    public ClientCreditConsumptionResponse getNetCreditsConsumed(Long clientId, Instant from, Instant to) {
        SpendSummaryRow row = ledgerRepository.findSpendSummary(clientId, from, to);
        return new ClientCreditConsumptionResponse(row.getNetCreditsConsumed(), from, to);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long clientId) {
        ClientCreditBalance row = balanceRepository.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credit balance row for client: " + clientId,
                        "client_credit_balance"));
        return new BalanceResponse(row.getBalance(), "segments", "SMS_SEGMENT", row.getLastModifiedDate());
    }

    @Transactional(readOnly = true)
    public LedgerHistoryResponse getLedgerHistory(Long clientId, int page, int size) {
        int effectiveSize = Math.min(size, 200);
        Pageable pageable = PageRequest.of(page, effectiveSize);
        Page<CreditLedgerEntry> pageResult = ledgerRepository.findByClientIdOrderByCreatedDateDesc(clientId, pageable);
        List<LedgerEntryDto> dtos = pageResult.getContent().stream()
                .map(entry -> new LedgerEntryDto(
                        entry.getCreatedDate(),
                        entry.getEntryType(),
                        entry.getAmount(),
                        entry.getBalanceAfter(),
                        entry.getReference()))
                .toList();
        return new LedgerHistoryResponse(dtos, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    /**
     * Core write method: acquires pessimistic lock on the client's balance row, appends a ledger entry,
     * and updates the running balance atomically. Called by TopupService (Plan 02) and
     * CreditReservationService (Plan 03).
     *
     * <p>TOPUP_PENDING entries (amount=0) pass through without modifying the balance — this is intentional.
     *
     * @param clientId  the client whose balance to update
     * @param type      the type of ledger entry
     * @param amount    signed amount: positive = credit, negative = debit
     * @param reference optional reference (topup_id, sendRequestId, etc.)
     * @throws InsufficientBalanceException if the resulting balance would be negative
     */
    public void applyLedgerEntry(Long clientId, LedgerEntryType type, long amount, String reference) {
        ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credit balance row for client: " + clientId,
                        "client_credit_balance"));

        long currentBalance = lockRow.getBalance();
        long newBalance = currentBalance + amount;

        if (newBalance < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=" + currentBalance + ", requested=" + Math.abs(amount),
                    clientId,
                    currentBalance,
                    Math.abs(amount));
        }

        CreditLedgerEntry entry = CreditLedgerEntry.builder()
                .clientId(clientId)
                .entryType(type)
                .amount(amount)
                .balanceAfter(newBalance)
                .reference(reference)
                .status(EntityStatus.ACTIVE)
                .build();
        ledgerRepository.save(entry);

        lockRow.setBalance(newBalance);
        balanceRepository.save(lockRow);
    }
}
