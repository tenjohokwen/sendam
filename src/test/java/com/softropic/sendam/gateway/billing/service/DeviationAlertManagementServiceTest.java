package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.AlertStatusTransitionException;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertDto;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlertRepository;
import com.softropic.sendam.gateway.billing.repo.DeviationAlertEvent;
import com.softropic.sendam.gateway.billing.repo.DeviationAlertEventRepository;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlertRepository;
import com.softropic.sendam.security.service.SecurityUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeviationAlertManagementService.
 * Covers list routing, detail fetch, acknowledge transitions, invalid-transition guard, and resolve.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviationAlertManagementServiceTest {

    @Mock private SegmentDeviationAlertRepository segmentRepo;
    @Mock private BalanceDeviationAlertRepository balanceRepo;
    @Mock private DeviationAlertEventRepository eventRepo;
    @Mock private SecurityUtil securityUtil;

    @InjectMocks
    private DeviationAlertManagementService service;

    // -------------------------------------------------------------------------
    // DEVMGMT-01a: listAlerts — type=BALANCE, no status filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-01a: listAlerts — type=BALANCE queries only balanceRepo; result has BALANCE type with null auditTrail")
    void listAlerts_balanceTypeFilter() {
        BalanceDeviationAlert balanceAlert = mock(BalanceDeviationAlert.class);
        when(balanceAlert.getId()).thenReturn(10L);
        when(balanceAlert.getAlertType()).thenReturn(DeviationAlertType.BALANCE);
        when(balanceAlert.getAlertStatus()).thenReturn(AlertStatus.OPEN);
        when(balanceAlert.getDelta()).thenReturn(50L);
        when(balanceAlert.getCreatedDate()).thenReturn(Instant.now());
        when(balanceAlert.getNexahBalance()).thenReturn(1050L);
        when(balanceAlert.getSendamBalance()).thenReturn(1000L);

        Page<BalanceDeviationAlert> page = new PageImpl<>(List.of(balanceAlert));
        when(balanceRepo.findByOptionalFilters(isNull(), any(Pageable.class))).thenReturn(page);

        Page<DeviationAlertDto> result = service.listAlerts(DeviationAlertType.BALANCE, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).type()).isEqualTo(DeviationAlertType.BALANCE);
        assertThat(result.getContent().get(0).auditTrail()).isNull();

        verify(segmentRepo, never()).findByOptionalFilters(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-01b: listAlerts — type=SEGMENT, no status filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-01b: listAlerts — type=SEGMENT queries only segmentRepo; result has SEGMENT type with null auditTrail")
    void listAlerts_segmentTypeFilter() {
        SegmentDeviationAlert segAlert = mock(SegmentDeviationAlert.class);
        when(segAlert.getId()).thenReturn(20L);
        when(segAlert.getAlertType()).thenReturn(DeviationAlertType.SEGMENT);
        when(segAlert.getAlertStatus()).thenReturn(AlertStatus.OPEN);
        when(segAlert.getDelta()).thenReturn(10L);
        when(segAlert.getCreatedDate()).thenReturn(Instant.now());
        when(segAlert.getSendRequestRef()).thenReturn("REF-001");
        when(segAlert.getClientId()).thenReturn(99L);
        when(segAlert.getFinancialAction()).thenReturn("DEBIT");
        when(segAlert.isClientFrozen()).thenReturn(false);
        when(segAlert.isPlatformFrozen()).thenReturn(false);

        Page<SegmentDeviationAlert> page = new PageImpl<>(List.of(segAlert));
        when(segmentRepo.findByOptionalFilters(eq(DeviationAlertType.SEGMENT), isNull(), any(Pageable.class)))
                .thenReturn(page);

        Page<DeviationAlertDto> result = service.listAlerts(DeviationAlertType.SEGMENT, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).type()).isEqualTo(DeviationAlertType.SEGMENT);
        assertThat(result.getContent().get(0).auditTrail()).isNull();

        verify(balanceRepo, never()).findByOptionalFilters(any(), any());
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-01c: listAlerts — type=null (all), merges both repos, BALANCE first (newer)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-01c: listAlerts — type=null merges both repos; BALANCE alert (newer createdDate) is first")
    void listAlerts_nullType_mergesBothRepos_sortedByCreatedDateDesc() {
        Instant tMinus1 = Instant.parse("2026-03-01T10:00:00Z");
        Instant tNow    = Instant.parse("2026-03-02T10:00:00Z");

        // SEGMENT alert — older
        SegmentDeviationAlert segAlert = mock(SegmentDeviationAlert.class);
        when(segAlert.getId()).thenReturn(1L);
        when(segAlert.getAlertType()).thenReturn(DeviationAlertType.SEGMENT);
        when(segAlert.getAlertStatus()).thenReturn(AlertStatus.OPEN);
        when(segAlert.getDelta()).thenReturn(5L);
        when(segAlert.getCreatedDate()).thenReturn(tMinus1);
        when(segAlert.getSendRequestRef()).thenReturn("REF-002");
        when(segAlert.getClientId()).thenReturn(1L);
        when(segAlert.getFinancialAction()).thenReturn("DEBIT");
        when(segAlert.isClientFrozen()).thenReturn(false);
        when(segAlert.isPlatformFrozen()).thenReturn(false);

        // BALANCE alert — newer
        BalanceDeviationAlert balanceAlert = mock(BalanceDeviationAlert.class);
        when(balanceAlert.getId()).thenReturn(2L);
        when(balanceAlert.getAlertType()).thenReturn(DeviationAlertType.BALANCE);
        when(balanceAlert.getAlertStatus()).thenReturn(AlertStatus.OPEN);
        when(balanceAlert.getDelta()).thenReturn(20L);
        when(balanceAlert.getCreatedDate()).thenReturn(tNow);
        when(balanceAlert.getNexahBalance()).thenReturn(1020L);
        when(balanceAlert.getSendamBalance()).thenReturn(1000L);

        when(segmentRepo.findByOptionalFilters(isNull(), isNull(), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(segAlert)));
        when(balanceRepo.findByOptionalFilters(isNull(), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(balanceAlert)));

        Page<DeviationAlertDto> result = service.listAlerts(null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        // BALANCE (newer) should be first
        assertThat(result.getContent().get(0).type()).isEqualTo(DeviationAlertType.BALANCE);
        assertThat(result.getContent().get(1).type()).isEqualTo(DeviationAlertType.SEGMENT);
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-02: getAlert — type=BALANCE, returns full auditTrail
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-02: getAlert — type=BALANCE returns DTO with populated auditTrail of size 1")
    void getAlert_balance_returnsFullAuditTrail() {
        long id = 5L;

        BalanceDeviationAlert alert = mock(BalanceDeviationAlert.class);
        when(alert.getId()).thenReturn(id);
        when(alert.getAlertType()).thenReturn(DeviationAlertType.BALANCE);
        when(alert.getAlertStatus()).thenReturn(AlertStatus.ACKNOWLEDGED);
        when(alert.getDelta()).thenReturn(30L);
        when(alert.getCreatedDate()).thenReturn(Instant.now());
        when(alert.getNexahBalance()).thenReturn(1030L);
        when(alert.getSendamBalance()).thenReturn(1000L);

        DeviationAlertEvent event = mock(DeviationAlertEvent.class);
        when(event.getId()).thenReturn(100L);
        when(event.getPreviousStatus()).thenReturn(AlertStatus.OPEN);
        when(event.getNewStatus()).thenReturn(AlertStatus.ACKNOWLEDGED);
        when(event.getAdminNote()).thenReturn("acknowledged by admin");
        when(event.getActedBy()).thenReturn("admin@test.com");
        when(event.getActedAt()).thenReturn(Instant.now());

        when(balanceRepo.findById(id)).thenReturn(Optional.of(alert));
        when(eventRepo.findByBalanceAlertIdFkOrderByActedAtAsc(id)).thenReturn(List.of(event));

        DeviationAlertDto dto = service.getAlert(DeviationAlertType.BALANCE, id);

        assertThat(dto.auditTrail()).hasSize(1);
        assertThat(dto.auditTrail().get(0).newStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-03a: acknowledge — OPEN alert → ACKNOWLEDGED, saves event
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-03a: acknowledge — OPEN SEGMENT alert transitions to ACKNOWLEDGED and saves event")
    void acknowledge_openAlert_transitionsToAcknowledged() {
        long id = 7L;
        when(securityUtil.getCurrentUserName()).thenReturn("admin@test.com");

        SegmentDeviationAlert alert = mock(SegmentDeviationAlert.class);
        when(alert.getId()).thenReturn(id);
        when(alert.getAlertType()).thenReturn(DeviationAlertType.SEGMENT);
        when(alert.getAlertStatus()).thenReturn(AlertStatus.OPEN);
        when(alert.getDelta()).thenReturn(5L);
        when(alert.getCreatedDate()).thenReturn(Instant.now());
        when(alert.getSendRequestRef()).thenReturn("REF-007");
        when(alert.getClientId()).thenReturn(1L);
        when(alert.getFinancialAction()).thenReturn("DEBIT");
        when(alert.isClientFrozen()).thenReturn(false);
        when(alert.isPlatformFrozen()).thenReturn(false);

        when(segmentRepo.findById(id)).thenReturn(Optional.of(alert));
        when(eventRepo.findBySegmentAlertIdFkOrderByActedAtAsc(id)).thenReturn(List.of());

        service.acknowledge(DeviationAlertType.SEGMENT, id, "my note");

        verify(alert).setAlertStatus(AlertStatus.ACKNOWLEDGED);
        verify(segmentRepo).save(alert);

        ArgumentCaptor<DeviationAlertEvent> eventCaptor = ArgumentCaptor.forClass(DeviationAlertEvent.class);
        verify(eventRepo).save(eventCaptor.capture());
        DeviationAlertEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getNewStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(savedEvent.getAdminNote()).isEqualTo("my note");
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-03b: acknowledge — RESOLVED alert → throws AlertStatusTransitionException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-03b: acknowledge — RESOLVED SEGMENT alert throws AlertStatusTransitionException; segmentRepo.save never called")
    void acknowledge_resolvedAlert_throwsTransitionException() {
        long id = 8L;

        SegmentDeviationAlert alert = mock(SegmentDeviationAlert.class);
        when(alert.getId()).thenReturn(id);
        when(alert.getAlertStatus()).thenReturn(AlertStatus.RESOLVED);

        when(segmentRepo.findById(id)).thenReturn(Optional.of(alert));

        assertThrows(AlertStatusTransitionException.class,
                () -> service.acknowledge(DeviationAlertType.SEGMENT, id, "note"));

        verify(segmentRepo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // DEVMGMT-04: resolve — ACKNOWLEDGED BALANCE alert → RESOLVED, saves event
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DEVMGMT-04: resolve — ACKNOWLEDGED BALANCE alert transitions to RESOLVED and saves event with RESOLVED newStatus")
    void resolve_acknowledgedBalance_transitionsToResolved() {
        long id = 9L;
        when(securityUtil.getCurrentUserName()).thenReturn("admin@test.com");

        BalanceDeviationAlert alert = mock(BalanceDeviationAlert.class);
        when(alert.getId()).thenReturn(id);
        when(alert.getAlertType()).thenReturn(DeviationAlertType.BALANCE);
        when(alert.getAlertStatus()).thenReturn(AlertStatus.ACKNOWLEDGED);
        when(alert.getDelta()).thenReturn(10L);
        when(alert.getCreatedDate()).thenReturn(Instant.now());
        when(alert.getNexahBalance()).thenReturn(1010L);
        when(alert.getSendamBalance()).thenReturn(1000L);

        when(balanceRepo.findById(id)).thenReturn(Optional.of(alert));
        when(eventRepo.findByBalanceAlertIdFkOrderByActedAtAsc(id)).thenReturn(List.of());

        service.resolve(DeviationAlertType.BALANCE, id, "resolved note");

        verify(alert).setAlertStatus(AlertStatus.RESOLVED);
        verify(balanceRepo).save(alert);

        ArgumentCaptor<DeviationAlertEvent> eventCaptor = ArgumentCaptor.forClass(DeviationAlertEvent.class);
        verify(eventRepo).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getNewStatus()).isEqualTo(AlertStatus.RESOLVED);
    }
}
