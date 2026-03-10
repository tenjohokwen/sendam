package com.softropic.sendam.client.repo;

import com.softropic.sendam.client.contract.SendRequestStatus;
import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

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

import java.time.Instant;

/**
 * JPA entity for SMS send requests submitted by clients.
 * sendStatus tracks the SMS lifecycle (ACCEPTED / SUBMITTED / COMPLETED / FAILED / FINALIZED / FAIL_FINALIZED / CANCELLED).
 * The inherited status column (from AbstractAuditingEntity) is always ACTIVE — it is NOT
 * used for SMS lifecycle; those are separate concerns on separate columns.
 */
@Slf4j
@Entity
@Table(name = "send_request", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SendRequest extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "send_request_id", nullable = false, length = 200)
    private String sendRequestId;

    @Column(name = "sender", nullable = false, length = 11)
    private String sender;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false, length = 30)
    private SendRequestStatus sendStatus = SendRequestStatus.ACCEPTED;

    @Column(name = "schedule_time")
    private Instant scheduleTime;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "segment_count", nullable = false)
    private int segmentCount;

    @Column(name = "reserved_credits", nullable = false)
    private long reservedCredits;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
