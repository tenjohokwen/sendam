package com.softropic.sendam.gateway.spend.repo;

import com.softropic.sendam.gateway.spend.contract.SpendSummaryRow;
import com.softropic.sendam.gateway.spend.contract.TopupHistoryRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

@org.springframework.stereotype.Repository
public interface SpendRepository extends Repository<Object, Long> {

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

    @Query(value = """
        SELECT
            t.id                AS id,
            t.client_id         AS client_id,
            t.amount            AS amount,
            t.transaction_id    AS transaction_id,
            t.payment_type      AS payment_type,
            t.account_number    AS account_number,
            t.topup_status      AS topup_status,
            t.created_date      AS created_date,
            t.approved_at       AS approved_at,
            t.rejected_at       AS rejected_at
        FROM main.topup_request t
        WHERE (:clientId     IS NULL OR t.client_id    = :clientId)
          AND (:topupStatus  IS NULL OR t.topup_status = :topupStatus)
          AND (:from         IS NULL OR t.created_date >= :from)
          AND (:to           IS NULL OR t.created_date <= :to)
        ORDER BY t.created_date DESC
        """, nativeQuery = true)
    List<TopupHistoryRow> findTopupHistory(
        @Param("clientId")    Long    clientId,
        @Param("topupStatus") String  topupStatus,
        @Param("from")        Instant from,
        @Param("to")          Instant to
    );
}
