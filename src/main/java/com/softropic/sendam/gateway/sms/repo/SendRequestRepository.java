package com.softropic.sendam.gateway.sms.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SendRequestRepository extends JpaRepository<SendRequest, Long> {

    Optional<SendRequest> findByClientIdAndSendRequestId(Long clientId, String sendRequestId);

    Optional<SendRequest> findBySendRequestId(String sendRequestId);

    @Query("SELECT s FROM SendRequest s " +
           "WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NOT NULL " +
           "ORDER BY s.scheduleTime ASC")
    Page<SendRequest> findScheduledAccepted(Pageable pageable);

    @Query("SELECT s FROM SendRequest s WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NOT NULL AND s.scheduleTime <= :now")
    List<SendRequest> findDueScheduledRequests(@Param("now") Instant now);

    @Query("SELECT s FROM SendRequest s WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NULL")
    List<SendRequest> findPendingImmediateRequests();

    @Query("SELECT DISTINCT s FROM SendRequest s JOIN SendRequestRecipient r ON r.sendRequestIdFk = s.id " +
           "WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUBMITTED " +
           "AND r.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUBMITTED " +
           "AND s.lastModifiedDate < :cutoff")
    List<SendRequest> findStaleSubmittedRequests(@Param("cutoff") Instant cutoff);

    @Query("SELECT s.id FROM SendRequest s WHERE s.finalizedAt < :cutoff " +
           "AND s.sendStatus IN (com.softropic.sendam.gateway.sms.contract.SendRequestStatus.FINALIZED, " +
           "com.softropic.sendam.gateway.sms.contract.SendRequestStatus.FAIL_FINALIZED)")
    List<Long> findFinalizedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM SendRequest s WHERE s.id IN :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);

    /**
     * Suspends all ACCEPTED scheduled sends for a specific client (client freeze).
     * Must be called within a @Transactional context.
     */
    @Modifying
    @Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED " +
           "WHERE s.clientId = :clientId " +
           "AND s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NOT NULL")
    int suspendScheduledForClient(@Param("clientId") Long clientId);

    /**
     * Resumes all SUSPENDED sends for a specific client (client unfreeze).
     * Must be called within a @Transactional context.
     */
    @Modifying
    @Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "WHERE s.clientId = :clientId " +
           "AND s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED")
    int resumeScheduledForClient(@Param("clientId") Long clientId);

    /**
     * Suspends all ACCEPTED scheduled sends across ALL clients (platform freeze).
     * Must be called within a @Transactional context.
     */
    @Modifying
    @Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED " +
           "WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NOT NULL")
    int suspendAllScheduled();

    /**
     * Resumes all SUSPENDED sends across ALL clients (platform unfreeze).
     * Must be called within a @Transactional context.
     */
    @Modifying
    @Query("UPDATE SendRequest s SET s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.ACCEPTED " +
           "WHERE s.sendStatus = com.softropic.sendam.gateway.sms.contract.SendRequestStatus.SUSPENDED")
    int resumeAllScheduled();
}
