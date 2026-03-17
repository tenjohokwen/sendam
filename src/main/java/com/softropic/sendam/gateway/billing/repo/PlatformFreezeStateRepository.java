package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface PlatformFreezeStateRepository extends JpaRepository<PlatformFreezeState, Long> {

    /**
     * Non-locking read — safe for checking freeze state without holding a lock.
     */
    @Query("SELECT p FROM PlatformFreezeState p")
    Optional<PlatformFreezeState> findState();

    /**
     * Pessimistic lock — used by PlatformFreezeService when transitioning freeze state.
     * Lock timeout of 2000ms prevents indefinite blocking under contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlatformFreezeState p")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<PlatformFreezeState> findForUpdate();
}
