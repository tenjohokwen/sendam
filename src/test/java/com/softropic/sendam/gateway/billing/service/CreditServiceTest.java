package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.ClientCreditConsumptionResponse;
import com.softropic.sendam.gateway.billing.contract.BalanceResponse;
import com.softropic.sendam.gateway.billing.contract.LedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.InsufficientBalanceException;
import com.softropic.sendam.gateway.billing.contract.SpendSummaryResponse;
import com.softropic.sendam.gateway.billing.contract.SpendSummaryRow;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalance;
import com.softropic.sendam.gateway.billing.repo.ClientCreditBalanceRepository;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerEntry;
import com.softropic.sendam.gateway.billing.repo.CreditLedgerRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private ClientCreditBalanceRepository balanceRepository;
    @Mock
    private CreditLedgerRepository ledgerRepository;

    @InjectMocks
    private CreditService creditService;

    @Test
    @DisplayName("getSpendSummary: success - returns spend summary from repository")
    void getSpendSummary_success() {
        Instant now = Instant.now();
        SpendSummaryRow row = mock(SpendSummaryRow.class);
        when(row.getSmsDebit()).thenReturn(100L);
        when(row.getSmsRefund()).thenReturn(10L);
        when(row.getTopupApproved()).thenReturn(500L);
        when(row.getSmsReservation()).thenReturn(50L);
        when(row.getNetCreditsConsumed()).thenReturn(140L);

        when(ledgerRepository.findSpendSummary(eq(1L), any(), any())).thenReturn(row);

        SpendSummaryResponse response = creditService.getSpendSummary(1L, now, now);

        assertThat(response.smsDebit()).isEqualTo(100L);
        assertThat(response.netCreditsConsumed()).isEqualTo(140L);
    }

    @Test
    @DisplayName("getNetCreditsConsumed: success - returns net consumption from repository")
    void getNetCreditsConsumed_success() {
        SpendSummaryRow row = mock(SpendSummaryRow.class);
        when(row.getNetCreditsConsumed()).thenReturn(140L);
        when(ledgerRepository.findSpendSummary(eq(1L), any(), any())).thenReturn(row);

        ClientCreditConsumptionResponse response = creditService.getNetCreditsConsumed(1L, null, null);

        assertThat(response.netCreditsConsumed()).isEqualTo(140L);
    }

    @Test
    @DisplayName("getBalance: success - returns current balance for client")
    void getBalance_success() {
        ClientCreditBalance row = new ClientCreditBalance();
        row.setBalance(100L);
        when(balanceRepository.findByClientId(1L)).thenReturn(Optional.of(row));

        BalanceResponse response = creditService.getBalance(1L);

        assertThat(response.availableBalance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("applyLedgerEntry: success - positive amount increases balance")
    void applyLedgerEntry_credit_success() {
        ClientCreditBalance lockRow = new ClientCreditBalance();
        lockRow.setBalance(100L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));

        creditService.applyLedgerEntry(1L, LedgerEntryType.TOPUP_APPROVED, 50L, "ref-1");

        assertThat(lockRow.getBalance()).isEqualTo(150L);
        verify(balanceRepository).save(lockRow);
        verify(ledgerRepository).save(argThat(e -> e.getAmount() == 50L && e.getBalanceAfter() == 150L));
    }

    @Test
    @DisplayName("applyLedgerEntry: success - negative amount decreases balance")
    void applyLedgerEntry_debit_success() {
        ClientCreditBalance lockRow = new ClientCreditBalance();
        lockRow.setBalance(100L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));

        creditService.applyLedgerEntry(1L, LedgerEntryType.SMS_DEBIT, -30L, "ref-2");

        assertThat(lockRow.getBalance()).isEqualTo(70L);
        verify(balanceRepository).save(lockRow);
    }

    @Test
    @DisplayName("applyLedgerEntry: failure - insufficient balance throws exception")
    void applyLedgerEntry_insufficientBalance() {
        ClientCreditBalance lockRow = new ClientCreditBalance();
        lockRow.setBalance(20L);
        when(balanceRepository.findByClientIdForUpdate(1L)).thenReturn(Optional.of(lockRow));

        assertThatThrownBy(() -> creditService.applyLedgerEntry(1L, LedgerEntryType.SMS_DEBIT, -30L, "ref-3"))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(balanceRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
}
