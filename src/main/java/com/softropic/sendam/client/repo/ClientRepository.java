package com.softropic.sendam.client.repo;

import com.softropic.sendam.client.contract.AdminClientDto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    /**
     * Returns all clients with their current credit balance via a JPQL LEFT JOIN.
     * COALESCE handles clients that somehow have no balance row (returns 0 as a safe default).
     * Ordered by creation date descending (most recently created first).
     */
    @Query("SELECT new com.softropic.sendam.client.contract.AdminClientDto(c.id, c.name, c.status, COALESCE(b.balance, 0L)) " +
           "FROM ClientEntity c LEFT JOIN ClientCreditBalance b ON b.clientId = c.id " +
           "ORDER BY c.createdDate DESC")
    List<AdminClientDto> findAllWithBalance();
}

