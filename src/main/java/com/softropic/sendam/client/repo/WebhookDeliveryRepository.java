package com.softropic.sendam.client.repo;

import com.softropic.sendam.client.contract.WebhookDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /**
     * Returns all delivery rows whose attempt lifecycle status matches and whose next
     * scheduled attempt is due (i.e. nextAttemptAt is before the given cutoff).
     * Used by the scheduled poller in WebhookService (Plan 05-02) to find due deliveries.
     */
    List<WebhookDelivery> findByAttemptStatusAndNextAttemptAtBefore(
            WebhookDeliveryStatus attemptStatus,
            Instant cutoff
    );
}
