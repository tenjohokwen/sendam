package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.TopupHistoryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface TopupRequestRepository extends JpaRepository<TopupRequestEntity, Long> {

    List<TopupRequestEntity> findByClientId(Long clientId);

    /**
     * Client-facing status query — enforces client isolation by requiring both clientId and topup id.
     */
    Optional<TopupRequestEntity> findByClientIdAndId(Long clientId, Long id);

    /**
     * Pessimistic write lock used exclusively in approve() to prevent double-approval race conditions.
     * 2-second lock timeout matches the pattern established by ClientCreditBalanceRepository.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopupRequestEntity t WHERE t.id = :id")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<TopupRequestEntity> findByIdForUpdate(@Param("id") Long id);

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
