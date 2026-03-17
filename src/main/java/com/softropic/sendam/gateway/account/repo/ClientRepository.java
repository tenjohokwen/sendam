package com.softropic.sendam.gateway.account.repo;

import com.softropic.sendam.gateway.account.contract.AdminClientDto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    /**
     * Returns all clients with their current credit balance via a JPQL LEFT JOIN.
     * COALESCE handles clients that somehow have no balance row (returns 0 as a safe default).
     * Ordered by creation date descending (most recently created first).
     */
    /**
     * Returns all clients with their current credit balance via a JPQL LEFT JOIN.
     * COALESCE handles clients that somehow have no balance row (returns 0 as a safe default).
     * Ordered by creation date descending (most recently created first).
     */
    @Query("SELECT new com.softropic.sendam.gateway.account.contract.AdminClientDto(c.id, c.name, c.status, COALESCE(b.balance, 0L)) " +
           "FROM ClientEntity c LEFT JOIN ClientCreditBalance b ON b.clientId = c.id " +
           "ORDER BY c.createdDate DESC")
    List<AdminClientDto> findAllWithBalance();

    /**
     * Acquires a pessimistic write lock on the client row for freeze/unfreeze state transitions.
     * Lock timeout of 2000ms prevents indefinite blocking under contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClientEntity c WHERE c.id = :id")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    Optional<ClientEntity> findByIdForUpdate(@Param("id") Long id);
}

