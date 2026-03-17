package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface PlatformCreditBalanceRepository extends JpaRepository<PlatformCreditBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PlatformCreditBalance b")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<PlatformCreditBalance> findForUpdate();

    @Query("SELECT b FROM PlatformCreditBalance b")
    Optional<PlatformCreditBalance> findBalance();
}
