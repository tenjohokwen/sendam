package com.softropic.sendam.client.service;

import com.softropic.sendam.client.contract.BalanceResponse;
import com.softropic.sendam.client.contract.LedgerEntryDto;
import com.softropic.sendam.client.contract.LedgerEntryType;
import com.softropic.sendam.client.contract.LedgerHistoryResponse;
import com.softropic.sendam.client.contract.exception.InsufficientBalanceException;
import com.softropic.sendam.client.repo.ClientCreditBalance;
import com.softropic.sendam.client.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.client.repo.CreditLedgerEntry;
import com.softropic.sendam.client.repo.CreditLedgerRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
