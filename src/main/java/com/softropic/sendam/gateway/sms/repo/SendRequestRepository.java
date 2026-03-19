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

    @Query(value = "SELECT * FROM main.send_request s " +
           "WHERE s.send_status = 'ACCEPTED' " +
           "AND (s.schedule_time IS NULL OR s.schedule_time <= :now) " +
           "ORDER BY s.schedule_time ASC NULLS FIRST " +
           "LIMIT :limit " +
           "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<SendRequest> findAndLockAcceptedRequests(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = "SELECT s.* FROM main.send_request s " +
           "WHERE s.send_status = 'SENDING' " +
           "AND s.last_modified_date < :cutoff " +
           "LIMIT :limit " +
           "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<SendRequest> findAndLockStuckSendingRequests(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query(value = "SELECT s.* FROM main.send_request s " +
           "WHERE s.send_status = 'SUBMITTED' " +
           "AND s.last_modified_date < :cutoff " +
           "LIMIT :limit " +
           "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<SendRequest> findAndLockStaleSubmittedRequests(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

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
