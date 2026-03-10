package com.softropic.sendam.client.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "client_credit_balance", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ClientCreditBalance extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false, unique = true)
    private Long clientId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
