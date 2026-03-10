package com.softropic.sendam.client.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ClientCreditBalanceRepository extends JpaRepository<ClientCreditBalance, Long> {

    Optional<ClientCreditBalance> findByClientId(Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ClientCreditBalance b WHERE b.clientId = :clientId")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<ClientCreditBalance> findByClientIdForUpdate(@Param("clientId") Long clientId);
}
