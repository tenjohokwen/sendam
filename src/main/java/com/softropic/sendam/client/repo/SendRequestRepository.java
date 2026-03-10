package com.softropic.sendam.client.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SendRequestRepository extends JpaRepository<SendRequest, Long> {

    Optional<SendRequest> findByClientIdAndSendRequestId(Long clientId, String sendRequestId);

    @Query("SELECT s FROM SendRequest s WHERE s.sendStatus = com.softropic.sendam.client.contract.SendRequestStatus.ACCEPTED " +
           "AND s.scheduleTime IS NOT NULL AND s.scheduleTime <= :now")
    List<SendRequest> findDueScheduledRequests(@Param("now") Instant now);
}
