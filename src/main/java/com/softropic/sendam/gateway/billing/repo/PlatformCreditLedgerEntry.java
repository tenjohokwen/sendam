package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
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
@Table(name = "platform_credit_ledger_entry", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlatformCreditLedgerEntry extends AbstractAuditingEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private PlatformLedgerEntryType entryType;

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
     * Optional reference: provider order id, topup id, etc.
     */
    @Column(name = "reference", length = 200)
    private String reference;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;
}
