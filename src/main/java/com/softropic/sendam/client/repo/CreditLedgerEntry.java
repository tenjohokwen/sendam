package com.softropic.sendam.client.repo;

import com.softropic.sendam.client.contract.LedgerEntryType;
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

@Entity
@Table(name = "credit_ledger_entry", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreditLedgerEntry extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    /**
     * Signed amount: positive = credit, negative = debit.
     */
    @Column(name = "amount", nullable = false)
    private long amount;

    /**
     * Running balance at the time this entry was written — not re-derived on read.
     */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /**
     * Optional reference: topup_id, sendRequestId, etc.
     */
    @Column(name = "reference", length = 200)
    private String reference;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
