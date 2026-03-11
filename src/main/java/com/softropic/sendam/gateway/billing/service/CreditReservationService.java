package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.InsufficientBalanceException;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerEntry;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Atomic credit reservation service. Called by the Phase 3 SMS send endpoint.
 *
 * <p>Every public method acquires a SELECT FOR UPDATE pessimistic lock on the
 * client_credit_balance row before reading or writing balance data. This is the
 * serialization point that guarantees two concurrent reserve() calls where the
 * sum exceeds the balance result in exactly one success and one
 * InsufficientBalanceException — balance never goes negative.
 *
 * <p>This service does NOT delegate to CreditService.applyLedgerEntry() to avoid
 * any implicit dependency on call ordering within a single transaction. It manages
 * its own lock acquisition independently.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CreditReservationService {

    private final ClientCreditBalanceRepository balanceRepository;
    private final CreditLedgerRepository ledgerRepository;

    /**
     * Reserves {@code amount} credits for the given client atomically.
     *
     * <p>Acquires SELECT FOR UPDATE on the balance row, checks that balance >= amount,
     * writes an SMS_RESERVATION ledger entry (amount = -N), and reduces the balance
     * by N. Returns the ledger entry id, which Phase 3 passes back to
     * {@link #debit(Long, Long, long)} or {@link #release(Long, Long)}.
     *
     * @param clientId  client whose credits to reserve
     * @param amount    number of credits to reserve; must be positive
     * @param reference caller-supplied correlation reference (e.g. send request id)
     * @return the id of the SMS_RESERVATION ledger entry (reservationId)
     * @throws IllegalArgumentException     if amount is not positive
     * @throws ResourceNotFoundException    if the client has no credit balance row
     * @throws InsufficientBalanceException if balance < amount
     */
    public long reserve(Long clientId, long amount, String reference) {
        if (amount <= 0) {
            throw new IllegalArgumentException("reserve amount must be positive");
        }

        ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credit balance row for client: " + clientId,
                        "client_credit_balance"));

        long currentBalance = lockRow.getBalance();

        if (currentBalance < amount) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=" + currentBalance + ", requested=" + amount,
                    clientId,
                    currentBalance,
                    amount);
        }

        long newBalance = currentBalance - amount;

        CreditLedgerEntry entry = CreditLedgerEntry.builder()
                .clientId(clientId)
                .entryType(LedgerEntryType.SMS_RESERVATION)
                .amount(-amount)
                .balanceAfter(newBalance)
                .reference(reference)
                .status(EntityStatus.ACTIVE)
                .build();
        CreditLedgerEntry saved = ledgerRepository.save(entry);

        lockRow.setBalance(newBalance);
        balanceRepository.save(lockRow);

        log.debug("Reserved {} credits for client {} (reservationId={})", amount, clientId, saved.getId());
        return saved.getId();
    }

    /**
     * Releases a previously reserved amount back to the client's balance.
     *
     * <p>Loads the original SMS_RESERVATION entry, verifies ownership, acquires
     * SELECT FOR UPDATE on the balance row, writes an SMS_REFUND entry (amount = +N),
     * and restores the balance.
     *
     * @param clientId      client whose reservation to release
     * @param reservationId id of the SMS_RESERVATION ledger entry returned by {@link #reserve}
     * @throws ResourceNotFoundException if the reservation entry does not exist or does not belong to this client
     * @throws IllegalStateException     if the entry is not of type SMS_RESERVATION
     */
    public void release(Long clientId, Long reservationId) {
        CreditLedgerEntry reservation = ledgerRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation entry not found: " + reservationId,
                        "credit_ledger_entry"));

        if (!reservation.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException(
                    "Reservation not found for client",
                    "credit_ledger_entry");
        }

        if (reservation.getEntryType() != LedgerEntryType.SMS_RESERVATION) {
            throw new IllegalStateException(
                    "Entry " + reservationId + " is not a reservation");
        }

        long releasedAmount = Math.abs(reservation.getAmount());

        ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credit balance row for client: " + clientId,
                        "client_credit_balance"));

        long newBalance = lockRow.getBalance() + releasedAmount;

        ledgerRepository.save(CreditLedgerEntry.builder()
                .clientId(clientId)
                .entryType(LedgerEntryType.SMS_REFUND)
                .amount(releasedAmount)
                .balanceAfter(newBalance)
                .reference("release:" + reservationId)
                .status(EntityStatus.ACTIVE)
                .build());

        lockRow.setBalance(newBalance);
        balanceRepository.save(lockRow);

        log.debug("Released reservation {} for client {} (+{} credits)", reservationId, clientId, releasedAmount);
    }

    /**
     * Finalises a reservation with the actual amount consumed.
     *
     * <p>Loads the original SMS_RESERVATION entry, verifies ownership and type,
     * acquires SELECT FOR UPDATE on the balance row, writes an SMS_DEBIT entry for
     * {@code actualAmount}. If {@code actualAmount} is less than the reserved amount
     * (over-reservation), also writes an SMS_REFUND entry for the difference and
     * restores the surplus to the balance.
     *
     * <p>The balance was already reduced by {@link #reserve}; this method does NOT
     * subtract {@code actualAmount} again — it only adjusts for over-reservation.
     *
     * @param clientId      client whose reservation to debit
     * @param reservationId id of the SMS_RESERVATION ledger entry returned by {@link #reserve}
     * @param actualAmount  actual credits to consume; must be positive and <= reservedAmount
     * @throws IllegalArgumentException  if actualAmount is not positive or exceeds the reserved amount
     * @throws ResourceNotFoundException if the reservation entry does not exist or does not belong to this client
     * @throws IllegalStateException     if the entry is not of type SMS_RESERVATION
     */
    public void debit(Long clientId, Long reservationId, long actualAmount) {
        if (actualAmount <= 0) {
            throw new IllegalArgumentException("actualAmount must be positive");
        }

        CreditLedgerEntry reservation = ledgerRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation entry not found: " + reservationId,
                        "credit_ledger_entry"));

        if (!reservation.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException(
                    "Reservation not found for client",
                    "credit_ledger_entry");
        }

        if (reservation.getEntryType() != LedgerEntryType.SMS_RESERVATION) {
            throw new IllegalStateException(
                    "Entry " + reservationId + " is not a reservation");
        }

        long reservedAmount = Math.abs(reservation.getAmount());

        if (actualAmount > reservedAmount) {
            throw new IllegalArgumentException(
                    "actualAmount " + actualAmount + " exceeds reserved amount " + reservedAmount);
        }

        ClientCreditBalance lockRow = balanceRepository.findByClientIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credit balance row for client: " + clientId,
                        "client_credit_balance"));

        long currentBalance = lockRow.getBalance();
        long overReservation = reservedAmount - actualAmount;

        // Write SMS_DEBIT entry — balance_after reflects the final state after any refund
        ledgerRepository.save(CreditLedgerEntry.builder()
                .clientId(clientId)
                .entryType(LedgerEntryType.SMS_DEBIT)
                .amount(-actualAmount)
                .balanceAfter(currentBalance + overReservation)
                .reference("debit:" + reservationId)
                .status(EntityStatus.ACTIVE)
                .build());

        if (overReservation > 0) {
            // Write SMS_REFUND for the over-reserved difference
            ledgerRepository.save(CreditLedgerEntry.builder()
                    .clientId(clientId)
                    .entryType(LedgerEntryType.SMS_REFUND)
                    .amount(overReservation)
                    .balanceAfter(currentBalance + overReservation)
                    .reference("over-reservation:" + reservationId)
                    .status(EntityStatus.ACTIVE)
                    .build());

            lockRow.setBalance(currentBalance + overReservation);
            balanceRepository.save(lockRow);

            log.debug("Debited {} credits for reservation {} (over-reservation refund: {})", actualAmount, reservationId, overReservation);
        } else {
            // Exact match — balance is already correct from reserve(), no lockRow update needed
            log.debug("Debited {} credits for reservation {} (exact amount, no refund)", actualAmount, reservationId);
        }
    }
}
