package com.softropic.sendam.gateway.webhook.repo;

import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.gateway.webhook.contract.WebhookStatsRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /**
     * WEBH-02: Returns paginated webhook deliveries filtered by optional clientId and attemptStatus.
     * Used by the admin monitor endpoint.
     */
    @Query("SELECT d FROM WebhookDelivery d WHERE " +
           "(:clientId IS NULL OR d.clientId = :clientId) AND " +
           "(:attemptStatus IS NULL OR d.attemptStatus = :attemptStatus)")
    Page<WebhookDelivery> findByFilters(
        @Param("clientId") Long clientId,
        @Param("attemptStatus") WebhookDeliveryStatus attemptStatus,
        Pageable pageable);

    /**
     * Returns all delivery rows whose attempt lifecycle status matches and whose next
     * scheduled attempt is due (i.e. nextAttemptAt is before the given cutoff).
     * Used by the scheduled poller in WebhookService (Plan 05-02) to find due deliveries.
     */
    List<WebhookDelivery> findByAttemptStatusAndNextAttemptAtBefore(
            WebhookDeliveryStatus attemptStatus,
            Instant cutoff
    );

    @Query(value = """
        SELECT
            COUNT(*)                                                          AS total_attempts,
            COUNT(CASE WHEN d.attempt_status = 'FAILED'    THEN 1 END)       AS failure_count,
            COUNT(CASE WHEN d.attempt_status = 'EXHAUSTED' THEN 1 END)       AS exhausted_count
        FROM main.webhook_delivery d
        """, nativeQuery = true)
    WebhookStatsRow findWebhookStats();
}
