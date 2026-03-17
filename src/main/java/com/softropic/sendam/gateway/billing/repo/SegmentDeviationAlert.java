package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;
import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;

import java.util.List;

@Slf4j
@Entity
@Table(name = "segment_deviation_alert", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SegmentDeviationAlert extends AbstractAuditingEntity {

    @Column(name = "send_request_id_fk", nullable = false)
    private Long sendRequestIdFk;

    @Column(name = "send_request_ref", nullable = false, length = 200)
    private String sendRequestRef;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private DeviationAlertType alertType;

    @Column(name = "expected_total", nullable = false)
    private long expectedTotal;

    @Column(name = "actual_total", nullable = false)
    private long actualTotal;

    @Column(name = "delta", nullable = false)
    private long delta;

    @Column(name = "shortfall_amount")
    private Long shortfallAmount;

    @Builder.Default
    @Column(name = "client_frozen", nullable = false)
    private boolean clientFrozen = false;

    @Builder.Default
    @Column(name = "platform_frozen", nullable = false)
    private boolean platformFrozen = false;

    @Column(name = "unrecovered_amount")
    private Long unrecoveredAmount;

    @Type(JsonType.class)
    @Column(name = "per_recipient_breakdown", columnDefinition = "jsonb")
    private List<RecipientDeviationEntry> perRecipientBreakdown;

    @Column(name = "financial_action", nullable = false, length = 30)
    private String financialAction;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 20)
    private AlertStatus alertStatus = AlertStatus.OPEN;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;

    /**
     * Per-recipient segment comparison record for the JSONB breakdown column.
     * Stores expected vs actual segments for each recipient in the send request.
     */
    public record RecipientDeviationEntry(
            String recipient,
            int expectedSegments,
            int actualSegments,
            int delta
    ) {}
}
