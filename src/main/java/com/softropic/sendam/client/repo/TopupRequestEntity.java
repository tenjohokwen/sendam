package com.softropic.sendam.client.repo;

import com.softropic.sendam.client.contract.TopupStatus;
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

import java.time.Instant;

/**
 * JPA entity for top-up requests submitted by clients.
 * topupStatus tracks the top-up lifecycle (PENDING_APPROVAL / APPROVED / REJECTED).
 * The inherited status column (from AbstractAuditingEntity) is always ACTIVE — it is NOT
 * used for top-up lifecycle; those are separate concerns on separate columns.
 */
@Entity
@Table(name = "topup_request", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TopupRequestEntity extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "transaction_id", nullable = false, length = 200)
    private String transactionId;

    @Column(name = "payment_type", nullable = false, length = 50)
    private String paymentType;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "topup_status", nullable = false, length = 30)
    private TopupStatus topupStatus = TopupStatus.PENDING_APPROVAL;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
