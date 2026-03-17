package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlert;
import com.softropic.sendam.gateway.billing.repo.SegmentDeviationAlertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class SegmentDeviationService {

    private final SegmentDeviationAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record SegmentDeviationAlertData(
            Long sendRequestPk,            // PK (numeric id) of send_request row — FK
            String sendRequestRef,         // human-readable sendRequestId string
            Long clientId,
            DeviationAlertType alertType,
            long expectedTotal,
            long actualTotal,
            long delta,
            Long shortfallAmount,          // null unless BOOK-05/06
            boolean clientFrozen,
            boolean platformFrozen,
            Long unrecoveredAmount,        // null unless BOOK-06
            List<SegmentDeviationAlert.RecipientDeviationEntry> perRecipientBreakdown,
            String financialAction         // "REFUNDED" | "EXTRA_DEBITED" | "SHORTFALL_ABSORBED"
    ) {}

    public void createAlert(SegmentDeviationAlertData data) {
        SegmentDeviationAlert alert = SegmentDeviationAlert.builder()
                .sendRequestIdFk(data.sendRequestPk())
                .sendRequestRef(data.sendRequestRef())
                .clientId(data.clientId())
                .alertType(data.alertType())
                .expectedTotal(data.expectedTotal())
                .actualTotal(data.actualTotal())
                .delta(data.delta())
                .shortfallAmount(data.shortfallAmount())
                .clientFrozen(data.clientFrozen())
                .platformFrozen(data.platformFrozen())
                .unrecoveredAmount(data.unrecoveredAmount())
                .perRecipientBreakdown(data.perRecipientBreakdown())
                .financialAction(data.financialAction())
                .status(EntityStatus.ACTIVE)
                .build();
        alertRepository.save(alert);

        // Publish audit event (consistent with PlatformFreezeService pattern)
        AuditEventType auditType = data.alertType() == DeviationAlertType.PLATFORM_FREEZE
                ? AuditEventType.PLATFORM_FREEZE_SHORTFALL
                : AuditEventType.SEGMENT_DEVIATION;
        eventPublisher.publishEvent(new DomainAuditEvent(
                auditType,
                data.clientId(),
                "system",
                "Segment deviation: sendRequestId=" + data.sendRequestRef()
                        + ", delta=" + data.delta()
                        + ", action=" + data.financialAction()
        ));

        log.info("Created {} deviation alert for sendRequestId={}, delta={}, action={}",
                data.alertType(), data.sendRequestRef(), data.delta(), data.financialAction());
    }
}
