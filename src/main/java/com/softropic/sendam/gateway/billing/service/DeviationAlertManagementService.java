package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.AlertStatusTransitionException;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertDto;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertEventDto;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.BalanceDeviationAlertRepository;
import com.softropic.sendam.gateway.billing.repo.DeviationAlertEvent;
import com.softropic.sendam.gateway.billing.repo.DeviationAlertEventRepository;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlertRepository;
import com.softropic.sendam.security.service.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for the OPEN → ACKNOWLEDGED → RESOLVED lifecycle of deviation alerts.
 *
 * <p>Routes between SegmentDeviationAlertRepository (SEGMENT/PLATFORM_FREEZE rows)
 * and BalanceDeviationAlertRepository (BALANCE rows).  The REST controller (Plan 04)
 * delegates every operation here.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeviationAlertManagementService {

    private final SegmentDeviationAlertRepository segmentRepo;
    private final BalanceDeviationAlertRepository balanceRepo;
    private final DeviationAlertEventRepository eventRepo;
    private final SecurityUtil securityUtil;

    // -------------------------------------------------------------------------
    // listAlerts
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of deviation alerts optionally filtered by type and/or status.
     *
     * <p>When {@code type} is {@code null} both repositories are queried (unpaged), the results
     * are merged, sorted by {@code createdDate DESC}, and then manually sliced to satisfy
     * the requested {@link Pageable}.</p>
     *
     * @param type        alert type filter; {@code null} = all types
     * @param alertStatus alert status filter; {@code null} = all statuses
     * @param pageable    pagination parameters
     * @return page of {@link DeviationAlertDto} with {@code auditTrail=null} (list view)
     */
    public Page<DeviationAlertDto> listAlerts(DeviationAlertType type,
                                              AlertStatus alertStatus,
                                              Pageable pageable) {
        if (type == DeviationAlertType.BALANCE) {
            return balanceRepo.findByOptionalFilters(alertStatus, pageable)
                    .map(a -> toDto(a, null));
        }

        if (type == DeviationAlertType.SEGMENT || type == DeviationAlertType.PLATFORM_FREEZE) {
            return segmentRepo.findByOptionalFilters(type, alertStatus, pageable)
                    .map(a -> toDto(a, null));
        }

        // type == null: merge both repos, sort, slice manually
        List<DeviationAlertDto> segmentDtos = segmentRepo
                .findByOptionalFilters(null, alertStatus, Pageable.unpaged())
                .stream()
                .map(a -> toDto(a, null))
                .collect(Collectors.toCollection(ArrayList::new));

        List<DeviationAlertDto> balanceDtos = balanceRepo
                .findByOptionalFilters(alertStatus, Pageable.unpaged())
                .stream()
                .map(a -> toDto(a, null))
                .collect(Collectors.toCollection(ArrayList::new));

        List<DeviationAlertDto> merged = new ArrayList<>(segmentDtos.size() + balanceDtos.size());
        merged.addAll(segmentDtos);
        merged.addAll(balanceDtos);
        merged.sort(Comparator.comparing(DeviationAlertDto::createdDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        List<DeviationAlertDto> pageContent = (start >= merged.size()) ? List.of() : merged.subList(start, end);
        return new PageImpl<>(pageContent, pageable, merged.size());
    }

    // -------------------------------------------------------------------------
    // getAlert
    // -------------------------------------------------------------------------

    /**
     * Returns a single alert with its full audit trail populated.
     *
     * @param type alert type (determines which repository to query)
     * @param id   alert primary key
     * @return {@link DeviationAlertDto} with {@code auditTrail} list populated
     * @throws ResourceNotFoundException if the alert does not exist
     */
    public DeviationAlertDto getAlert(DeviationAlertType type, Long id) {
        if (type == DeviationAlertType.BALANCE) {
            BalanceDeviationAlert alert = balanceRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Balance deviation alert not found: " + id, "BalanceDeviationAlert"));
            List<DeviationAlertEventDto> trail = eventRepo
                    .findByBalanceAlertIdFkOrderByActedAtAsc(id)
                    .stream()
                    .map(this::toEventDto)
                    .toList();
            return toDto(alert, trail);
        }

        // SEGMENT or PLATFORM_FREEZE
        SegmentDeviationAlert alert = segmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segment deviation alert not found: " + id, "SegmentDeviationAlert"));
        List<DeviationAlertEventDto> trail = eventRepo
                .findBySegmentAlertIdFkOrderByActedAtAsc(id)
                .stream()
                .map(this::toEventDto)
                .toList();
        return toDto(alert, trail);
    }

    // -------------------------------------------------------------------------
    // acknowledge
    // -------------------------------------------------------------------------

    /**
     * Transitions an alert from OPEN to ACKNOWLEDGED.
     *
     * <p>Saves a {@link DeviationAlertEvent} row before updating the alert entity.
     * Throws {@link AlertStatusTransitionException} if the current status does not
     * allow the ACKNOWLEDGED transition (e.g. RESOLVED alerts).</p>
     *
     * @param type alert type
     * @param id   alert primary key
     * @param note mandatory admin note
     * @return updated {@link DeviationAlertDto} with fresh audit trail
     */
    public DeviationAlertDto acknowledge(DeviationAlertType type, Long id, String note) {
        if (type == DeviationAlertType.BALANCE) {
            BalanceDeviationAlert alert = balanceRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Balance deviation alert not found: " + id, "BalanceDeviationAlert"));
            guardTransition(alert.getAlertStatus(), AlertStatus.ACKNOWLEDGED, id);
            saveEvent(type, id, null, alert.getAlertStatus(), AlertStatus.ACKNOWLEDGED, note);
            alert.setAlertStatus(AlertStatus.ACKNOWLEDGED);
            balanceRepo.save(alert);
            List<DeviationAlertEventDto> trail = eventRepo
                    .findByBalanceAlertIdFkOrderByActedAtAsc(id)
                    .stream()
                    .map(this::toEventDto)
                    .toList();
            return toDto(alert, trail);
        }

        // SEGMENT or PLATFORM_FREEZE
        SegmentDeviationAlert alert = segmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segment deviation alert not found: " + id, "SegmentDeviationAlert"));
        guardTransition(alert.getAlertStatus(), AlertStatus.ACKNOWLEDGED, id);
        saveEvent(type, null, id, alert.getAlertStatus(), AlertStatus.ACKNOWLEDGED, note);
        alert.setAlertStatus(AlertStatus.ACKNOWLEDGED);
        segmentRepo.save(alert);
        List<DeviationAlertEventDto> trail = eventRepo
                .findBySegmentAlertIdFkOrderByActedAtAsc(id)
                .stream()
                .map(this::toEventDto)
                .toList();
        return toDto(alert, trail);
    }

    // -------------------------------------------------------------------------
    // resolve
    // -------------------------------------------------------------------------

    /**
     * Transitions an alert from OPEN or ACKNOWLEDGED to RESOLVED.
     *
     * <p>Same structure as {@link #acknowledge} but target status is RESOLVED.</p>
     *
     * @param type alert type
     * @param id   alert primary key
     * @param note mandatory admin note
     * @return updated {@link DeviationAlertDto} with fresh audit trail
     */
    public DeviationAlertDto resolve(DeviationAlertType type, Long id, String note) {
        if (type == DeviationAlertType.BALANCE) {
            BalanceDeviationAlert alert = balanceRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Balance deviation alert not found: " + id, "BalanceDeviationAlert"));
            guardTransition(alert.getAlertStatus(), AlertStatus.RESOLVED, id);
            saveEvent(type, id, null, alert.getAlertStatus(), AlertStatus.RESOLVED, note);
            alert.setAlertStatus(AlertStatus.RESOLVED);
            balanceRepo.save(alert);
            List<DeviationAlertEventDto> trail = eventRepo
                    .findByBalanceAlertIdFkOrderByActedAtAsc(id)
                    .stream()
                    .map(this::toEventDto)
                    .toList();
            return toDto(alert, trail);
        }

        // SEGMENT or PLATFORM_FREEZE
        SegmentDeviationAlert alert = segmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segment deviation alert not found: " + id, "SegmentDeviationAlert"));
        guardTransition(alert.getAlertStatus(), AlertStatus.RESOLVED, id);
        saveEvent(type, null, id, alert.getAlertStatus(), AlertStatus.RESOLVED, note);
        alert.setAlertStatus(AlertStatus.RESOLVED);
        segmentRepo.save(alert);
        List<DeviationAlertEventDto> trail = eventRepo
                .findBySegmentAlertIdFkOrderByActedAtAsc(id)
                .stream()
                .map(this::toEventDto)
                .toList();
        return toDto(alert, trail);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void guardTransition(AlertStatus current, AlertStatus target, Long id) {
        if (!current.canTransitionTo(target)) {
            throw new AlertStatusTransitionException(
                    "Cannot acknowledge alert " + id + " in status " + current, id, current);
        }
    }

    /**
     * Persists a {@link DeviationAlertEvent}.
     * Exactly one of {@code balanceAlertIdFk} / {@code segmentAlertIdFk} is non-null per call.
     */
    private void saveEvent(DeviationAlertType type,
                           Long balanceAlertIdFk,
                           Long segmentAlertIdFk,
                           AlertStatus previousStatus,
                           AlertStatus newStatus,
                           String note) {
        DeviationAlertEvent event = DeviationAlertEvent.builder()
                .alertType(type)
                .balanceAlertIdFk(balanceAlertIdFk)
                .segmentAlertIdFk(segmentAlertIdFk)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .adminNote(note)
                .actedBy(securityUtil.getCurrentUserName())
                .actedAt(Instant.now())
                .build();
        eventRepo.save(event);
    }

    // --- DTO mappers ---

    private DeviationAlertDto toDto(SegmentDeviationAlert a, List<DeviationAlertEventDto> trail) {
        List<DeviationAlertDto.RecipientBreakdownDto> breakdown = null;
        if (a.getPerRecipientBreakdown() != null) {
            breakdown = a.getPerRecipientBreakdown().stream()
                    .map(e -> new DeviationAlertDto.RecipientBreakdownDto(
                            e.recipient(), e.expectedSegments(), e.actualSegments(), e.delta()))
                    .toList();
        }
        return new DeviationAlertDto(
                a.getId(),
                a.getAlertType(),
                a.getAlertStatus(),
                a.getDelta(),
                a.getCreatedDate(),
                a.getSendRequestRef(),
                a.getClientId(),
                a.getShortfallAmount(),
                a.getUnrecoveredAmount(),
                a.getFinancialAction(),
                a.isClientFrozen(),
                a.isPlatformFrozen(),
                breakdown,
                null,  // nexahBalance — not applicable for SEGMENT/PLATFORM_FREEZE
                null,  // sendamBalance — not applicable for SEGMENT/PLATFORM_FREEZE
                trail
        );
    }

    private DeviationAlertDto toDto(BalanceDeviationAlert a, List<DeviationAlertEventDto> trail) {
        return new DeviationAlertDto(
                a.getId(),
                a.getAlertType(),
                a.getAlertStatus(),
                a.getDelta(),
                a.getCreatedDate(),
                null,  // sendRequestRef — not applicable for BALANCE
                null,  // clientId — not applicable for BALANCE
                null,  // shortfallAmount — not applicable for BALANCE
                null,  // unrecoveredAmount — not applicable for BALANCE
                null,  // financialAction — not applicable for BALANCE
                null,  // clientFrozen — not applicable for BALANCE
                null,  // platformFrozen — not applicable for BALANCE
                null,  // perRecipientBreakdown — not applicable for BALANCE
                a.getNexahBalance(),
                a.getSendamBalance(),
                trail
        );
    }

    private DeviationAlertEventDto toEventDto(DeviationAlertEvent e) {
        return new DeviationAlertEventDto(
                e.getId(),
                e.getPreviousStatus(),
                e.getNewStatus(),
                e.getAdminNote(),
                e.getActedBy(),
                e.getActedAt()
        );
    }
}
