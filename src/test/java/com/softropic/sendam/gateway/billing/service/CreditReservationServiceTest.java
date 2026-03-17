package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.InsufficientBalanceException;
import com.softropic.sendam.gateway.billing.contract.PlatformFrozenException;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerEntry;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerRepository;
import com.softropic.sendam.gateway.billing.service.PlatformFreezeService;
import com.softropic.sendam.gateway.account.contract.AccountFrozenException;
import com.softropic.sendam.gateway.account.service.ClientFreezeService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lombok.extern.slf4j.Slf4j;

@ExtendWith(MockitoExtension.class)
@Slf4j
class CreditReservationServiceTest {

    @Mock
    private ClientCreditBalanceRepository balanceRepository;

    @Mock
    private CreditLedgerRepository ledgerRepository;

    @Mock
    private ClientFreezeService clientFreezeService;

    @Mock
    private PlatformFreezeService platformFreezeService;

    @InjectMocks
    private CreditReservationService service;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ClientCreditBalance lockRowWithBalance(long balance) {
        ClientCreditBalance row = new ClientCreditBalance();
        row.setClientId(1L);
        row.setBalance(balance);
        return row;
    }

    private CreditLedgerEntry reservationEntry(long id, long clientId, long reservedAmount) {
        CreditLedgerEntry entry = new CreditLedgerEntry();
        // Use reflection-free approach: build via setter
        entry.setClientId(clientId);
        entry.setEntryType(LedgerEntryType.SMS_RESERVATION);
        entry.setAmount(-reservedAmount);
        entry.setBalanceAfter(0L); // not relevant for these tests
        return entry;
    }

    // -------------------------------------------------------------------------
    // TEST 1: reserve — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve succeeds when balance is sufficient — returns reservationId, writes entry, reduces balance")
    void reserve_success() {
        ClientCreditBalance lockRow = lockRowWithBalance(100L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));
        when(ledgerRepository.save(any())).thenAnswer(inv -> {
            CreditLedgerEntry e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        long reservationId = service.reserve(1L, 30L, "ref-001");

        assertThat(reservationId).isEqualTo(42L);
        verify(balanceRepository).save(lockRow);
        assertThat(lockRow.getBalance()).isEqualTo(70L);
        verify(ledgerRepository).save(
                argCapture(LedgerEntryType.SMS_RESERVATION, -30L, 70L));
    }

    /**
     * Helper that matches an entry capture without ArgumentCaptor boilerplate.
     * Returns the argument that would match the invocation for verify() chaining.
     */
    private CreditLedgerEntry argCapture(LedgerEntryType expectedType, long expectedAmount, long expectedBalanceAfter) {
        return org.mockito.ArgumentMatchers.argThat(entry ->
                entry != null
                && entry.getEntryType() == expectedType
                && entry.getAmount() == expectedAmount
                && entry.getBalanceAfter() == expectedBalanceAfter);
    }

    // -------------------------------------------------------------------------
    // TEST 2: reserve — insufficient balance throws and no writes happen
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve throws InsufficientBalanceException when balance < amount — no writes made")
    void reserve_insufficientBalance() {
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRowWithBalance(20L)));

        assertThrows(InsufficientBalanceException.class, () -> service.reserve(1L, 30L, "ref-002"));

        verify(ledgerRepository, never()).save(any());
        verify(balanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // TEST 3: concurrent reserve — only the first succeeds
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("concurrent reserve: first call succeeds, second throws InsufficientBalanceException; balance never goes negative")
    void concurrentReserve_onlyOneSucceeds() {
        AtomicLong sharedBalance = new AtomicLong(50L);

        when(balanceRepository.findByClientIdForUpdate(1L)).thenAnswer(invocation ->
                Optional.of(lockRowWithBalance(sharedBalance.get())));

        when(balanceRepository.save(any())).thenAnswer(invocation -> {
            ClientCreditBalance saved = invocation.getArgument(0);
            sharedBalance.set(saved.getBalance());
            return saved;
        });

        when(ledgerRepository.save(any())).thenAnswer(inv -> {
            CreditLedgerEntry e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        // First call — should succeed; balance drops from 50 to 20
        service.reserve(1L, 30L, "req-A");

        // Second call — balance is now 20, requesting 30 → should fail
        assertThrows(InsufficientBalanceException.class, () -> service.reserve(1L, 30L, "req-B"));

        // Only the first reserve committed a balance update
        verify(balanceRepository, times(1)).save(any());
        assertThat(sharedBalance.get())
                .as("balance must not be negative")
                .isEqualTo(20L);
    }

    // -------------------------------------------------------------------------
    // TEST 4: release — restores balance and writes SMS_REFUND
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("release writes SMS_REFUND and restores balance to pre-reservation level")
    void release_success() {
        CreditLedgerEntry reservation = reservationEntry(42L, 1L, 30L);
        when(ledgerRepository.findById(42L)).thenReturn(Optional.of(reservation));
        ClientCreditBalance lockRow = lockRowWithBalance(70L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));

        service.release(1L, 42L);

        verify(balanceRepository).save(lockRow);
        assertThat(lockRow.getBalance()).isEqualTo(100L);
        verify(ledgerRepository).save(argCapture(LedgerEntryType.SMS_REFUND, 30L, 100L));
    }

    // -------------------------------------------------------------------------
    // TEST 5: debit with exact amount — no refund entry, no balance update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("debit with exact amount writes only SMS_DEBIT; no SMS_REFUND written and balance row not updated")
    void debit_exactAmount() {
        CreditLedgerEntry reservation = reservationEntry(42L, 1L, 30L);
        when(ledgerRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRowWithBalance(70L)));

        service.debit(1L, 42L, 30L);

        verify(ledgerRepository, times(1)).save(any()); // SMS_DEBIT only
        verify(balanceRepository, never()).save(any()); // balance already correct
    }

    // -------------------------------------------------------------------------
    // TEST 6: debit with over-reservation — refund entry written, balance updated
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("debit with over-reservation writes SMS_DEBIT and SMS_REFUND; balance updated by over-reservation amount")
    void debit_overReservation() {
        CreditLedgerEntry reservation = reservationEntry(42L, 1L, 30L);
        when(ledgerRepository.findById(42L)).thenReturn(Optional.of(reservation));
        ClientCreditBalance lockRow = lockRowWithBalance(70L); // balance after reserving 30 from 100
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));

        service.debit(1L, 42L, 20L); // actual=20, reserved=30 → over-reservation=10

        // Two ledger saves: SMS_DEBIT + SMS_REFUND
        verify(ledgerRepository, times(2)).save(any());
        verify(ledgerRepository).save(argCapture(LedgerEntryType.SMS_DEBIT, -20L, 80L));
        verify(ledgerRepository).save(argCapture(LedgerEntryType.SMS_REFUND, 10L, 80L));

        // Balance updated to 70 + 10 = 80
        verify(balanceRepository).save(lockRow);
        assertThat(lockRow.getBalance()).isEqualTo(80L);
    }

    // -------------------------------------------------------------------------
    // TEST 7: reserve with non-positive amount is rejected before any DB call
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve with negative or zero amount throws IllegalArgumentException before touching the DB")
    void reserve_negativeAmountRejected() {
        assertThatThrownBy(() -> service.reserve(1L, -5L, "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserve amount must be positive");

        verify(balanceRepository, never()).findByClientIdForUpdate(any());
    }

    // -------------------------------------------------------------------------
    // TEST 8: reserve throws AccountFrozenException when client is frozen (CFREEZE-01)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve throws AccountFrozenException when client is frozen; balance DB not touched")
    void reserve_throwsAccountFrozenException_whenClientFrozen() {
        when(clientFreezeService.isFrozen(1L)).thenReturn(true);

        assertThrows(AccountFrozenException.class, () -> service.reserve(1L, 10L, "ref-frozen"));

        // Freeze is a short-circuit: balance row must never be locked
        verify(balanceRepository, never()).findByClientIdForUpdate(any());
        verify(ledgerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // TEST 9: reserve throws PlatformFrozenException when platform is frozen (PFLAT-01)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve throws PlatformFrozenException when platform is frozen; balance DB not touched")
    void reserve_throwsPlatformFrozenException_whenPlatformFrozen() {
        when(clientFreezeService.isFrozen(1L)).thenReturn(false);
        when(platformFreezeService.isFrozen()).thenReturn(true);

        assertThrows(PlatformFrozenException.class, () -> service.reserve(1L, 10L, "ref-pf"));

        // Platform freeze is also a short-circuit before any DB locking
        verify(balanceRepository, never()).findByClientIdForUpdate(any());
        verify(ledgerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // TEST 10: reserve proceeds normally when neither client nor platform is frozen
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reserve proceeds to balance check when both freeze checks return false")
    void reserve_proceedsNormally_whenNotFrozen() {
        when(clientFreezeService.isFrozen(1L)).thenReturn(false);
        when(platformFreezeService.isFrozen()).thenReturn(false);

        ClientCreditBalance lockRow = lockRowWithBalance(100L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));
        when(ledgerRepository.save(any())).thenAnswer(inv -> {
            CreditLedgerEntry e = inv.getArgument(0);
            e.setId(77L);
            return e;
        });

        long reservationId = service.reserve(1L, 25L, "ref-ok");

        assertThat(reservationId).isEqualTo(77L);
        assertThat(lockRow.getBalance()).isEqualTo(75L);
        verify(balanceRepository).save(lockRow);
    }
}
