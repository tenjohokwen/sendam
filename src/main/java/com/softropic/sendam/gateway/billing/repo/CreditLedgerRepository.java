package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.SpendSummaryRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    Page<CreditLedgerEntry> findByClientIdOrderByCreatedDateDesc(Long clientId, Pageable pageable);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_DEBIT'
                              THEN ABS(e.amount) ELSE 0 END), 0)          AS sms_debit,
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_REFUND'
                              THEN e.amount      ELSE 0 END), 0)           AS sms_refund,
            COALESCE(SUM(CASE WHEN e.entry_type = 'TOPUP_APPROVED'
                              THEN e.amount      ELSE 0 END), 0)           AS topup_approved,
            COALESCE(SUM(CASE WHEN e.entry_type = 'SMS_RESERVATION'
                              THEN ABS(e.amount) ELSE 0 END), 0)           AS sms_reservation,
            COALESCE(SUM(CASE WHEN e.entry_type IN ('SMS_DEBIT', 'SMS_RESERVATION')
                              THEN ABS(e.amount)
                              WHEN e.entry_type IN ('SMS_REFUND', 'TOPUP_APPROVED')
                              THEN -e.amount
                              ELSE 0 END), 0)                              AS net_credits_consumed
        FROM main.credit_ledger_entry e
        WHERE (:clientId IS NULL OR e.client_id = :clientId)
          AND (:from IS NULL     OR e.created_date >= :from)
          AND (:to   IS NULL     OR e.created_date <= :to)
        """, nativeQuery = true)
    SpendSummaryRow findSpendSummary(
        @Param("clientId") Long clientId,
        @Param("from")     Instant from,
        @Param("to")       Instant to
    );
}
