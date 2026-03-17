package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.PlatformBalanceResponse;
import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.provider.nexah.infrastructure.NexahClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BalanceReconciliationJob — covers all 3 reconcile() paths:
 * exact match (no alert), mismatch (alert created), Nexah unavailable (skip cycle).
 */
@ExtendWith(MockitoExtension.class)
class BalanceReconciliationJobTest {

    @Mock
    private NexahClient nexahClient;

    @Mock
    private PlatformCreditService platformCreditService;

    @Mock
    private BalanceDeviationAlertService alertService;

    @InjectMocks
    private BalanceReconciliationJob job;

    // --- REC-01: Nexah balance equals Sendam balance — no alert created ---------

    @Test
    @DisplayName("REC-01: exact match — no alert created")
    void reconcile_exactMatch_noAlertCreated() {
        when(nexahClient.fetchCreditBalance()).thenReturn(1000L);
        when(platformCreditService.getBalance())
                .thenReturn(new PlatformBalanceResponse(1000L, Instant.now()));

        job.reconcile();

        verify(alertService, never()).createAlert(anyLong(), anyLong(), anyLong());
    }

    // --- REC-02: Nexah balance differs from Sendam balance — alert created ------

    @Test
    @DisplayName("REC-02: mismatch — alert created with correct nexahBalance, sendamBalance, delta")
    void reconcile_mismatch_alertCreatedWithCorrectDelta() {
        when(nexahClient.fetchCreditBalance()).thenReturn(1050L);
        when(platformCreditService.getBalance())
                .thenReturn(new PlatformBalanceResponse(1000L, Instant.now()));

        job.reconcile();

        verify(alertService).createAlert(1050L, 1000L, 50L);
    }

    // --- REC-03: Nexah unavailable — skip cycle, no alert, no balance read ------

    @Test
    @DisplayName("REC-03: Nexah unavailable — skip cycle with no alert and no platform balance read")
    void reconcile_nexahUnavailable_skipsWithNoAlert() {
        when(nexahClient.fetchCreditBalance())
                .thenThrow(new ProviderUnavailableException("Nexah /smscredit returned null response"));

        job.reconcile();

        verify(alertService, never()).createAlert(anyLong(), anyLong(), anyLong());
        // Short-circuit: Sendam balance must NOT be read when Nexah is unavailable
        verify(platformCreditService, never()).getBalance();
    }
}
