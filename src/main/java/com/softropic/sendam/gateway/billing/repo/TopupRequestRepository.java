package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

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
}
