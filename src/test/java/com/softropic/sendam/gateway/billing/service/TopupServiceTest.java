package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.*;
import com.softropic.sendam.gateway.billing.repo.TopupRequestEntity;
import com.softropic.sendam.gateway.billing.repo.TopupRequestRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopupServiceTest {

    @Mock
    private TopupRequestRepository topupRepository;
    @Mock
    private CreditService creditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TopupService topupService;

    @Test
    @DisplayName("getTopupHistory: success - returns mapped history items from repository")
    void getTopupHistory_success() {
        TopupHistoryRow row = mock(TopupHistoryRow.class);
        when(row.getId()).thenReturn(1L);
        when(row.getAmount()).thenReturn(500L);
        when(row.getCreatedDate()).thenReturn(Timestamp.from(Instant.now()));
        when(row.getTopupStatus()).thenReturn("APPROVED");

        when(topupRepository.findTopupHistory(any(), any(), any(), any())).thenReturn(List.of(row));

        TopupHistoryResponse response = topupService.getTopupHistory(100L, "APPROVED", null, null);

        assertThat(response.topups()).hasSize(1);
        assertThat(response.topups().get(0).amount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("createTopup: success - persists request and writes informational ledger entry")
    void createTopup_success() {
        CreateTopupRequest request = new CreateTopupRequest(500L, "tx-123", "MOMO", "670000000");
        TopupRequestEntity saved = TopupRequestEntity.builder().id(1L).amount(500L).build();
        
        when(topupRepository.save(any())).thenReturn(saved);

        CreateTopupResponse response = topupService.createTopup(100L, request);

        assertThat(response.topupId()).isEqualTo("top_1");
        assertThat(response.status()).isEqualTo(TopupStatus.PENDING_APPROVAL);
        
        verify(topupRepository).save(any());
        verify(creditService).applyLedgerEntry(eq(100L), eq(LedgerEntryType.TOPUP_PENDING), eq(0L), eq("top_1"));
    }

    @Test
    @DisplayName("approve: success - credits balance and updates status")
    void approve_success() {
        TopupRequestEntity entity = TopupRequestEntity.builder()
                .id(1L)
                .clientId(100L)
                .amount(500L)
                .topupStatus(TopupStatus.PENDING_APPROVAL)
                .build();

        when(topupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        TopupStatusResponse response = topupService.approve("top_1");

        assertThat(response.status()).isEqualTo(TopupStatus.APPROVED);
        verify(creditService).applyLedgerEntry(eq(100L), eq(LedgerEntryType.TOPUP_APPROVED), eq(500L), eq("top_1"));
        verify(topupRepository).save(entity);
    }

    @Test
    @DisplayName("approve: failure - already processed throws exception")
    void approve_alreadyProcessed() {
        TopupRequestEntity entity = TopupRequestEntity.builder()
                .id(1L)
                .topupStatus(TopupStatus.APPROVED)
                .build();

        when(topupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> topupService.approve("top_1"))
                .isInstanceOf(TopupAlreadyProcessedException.class);
        
        verify(creditService, never()).applyLedgerEntry(anyLong(), any(), anyLong(), anyString());
    }
}
