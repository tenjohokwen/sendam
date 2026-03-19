package com.softropic.sendam.gateway.sms.repo;

import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.apache.commons.lang3.builder.ToStringBuilder;

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

/**
 * JPA entity for individual recipients within an SMS send request.
 * sendStatus tracks per-recipient delivery state.
 * The inherited status column (from AbstractAuditingEntity) is always ACTIVE — it is NOT
 * used for recipient lifecycle; those are separate concerns on separate columns.
 */
@Entity
@Table(name = "send_request_recipient", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SendRequestRecipient extends AbstractAuditingEntity {

    @Column(name = "send_request_id_fk", nullable = false)
    private Long sendRequestIdFk;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "recipient", nullable = false, length = 20)
    private String recipient;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false, length = 30)
    private SendRequestStatus sendStatus = SendRequestStatus.ACCEPTED;

    @Column(name = "gateway_message_id", length = 100)
    private String gatewayMessageId;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "segments_consumed")
    private Integer segmentsConsumed;

    @Column(name = "expected_segments", nullable = false)
    private int expectedSegments;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("sendRequestIdFk", sendRequestIdFk)
                .append("clientId", clientId)
                .append("recipient", recipient)
                .append("sendStatus", sendStatus)
                .append("gatewayMessageId", gatewayMessageId)
                .append("providerMessageId", providerMessageId)
                .append("segmentsConsumed", segmentsConsumed)
                .append("expectedSegments", expectedSegments)
                .append("status", status)
                .toString();
    }
}
