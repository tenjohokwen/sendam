package com.softropic.sendam.gateway.health.repo;

import com.softropic.sendam.gateway.health.contract.ProviderStatsRow;
import com.softropic.sendam.gateway.health.contract.WebhookStatsRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface HealthRepository extends Repository<Object, Long> {

    @Query(value = """
        SELECT
            COUNT(*)                                                          AS total_attempts,
            COUNT(CASE WHEN d.attempt_status = 'FAILED'    THEN 1 END)       AS failure_count,
            COUNT(CASE WHEN d.attempt_status = 'EXHAUSTED' THEN 1 END)       AS exhausted_count
        FROM main.webhook_delivery d
        """, nativeQuery = true)
    WebhookStatsRow findWebhookStats();

    @Query(value = """
        SELECT
            COUNT(CASE WHEN r.send_status NOT IN ('ACCEPTED', 'CANCELLED') THEN 1 END) AS total_submitted,
            COUNT(CASE WHEN r.send_status IN ('COMPLETED', 'FAILED')        THEN 1 END) AS dr_received,
            COUNT(CASE WHEN r.send_status = 'FAILED'                        THEN 1 END) AS failed_count
        FROM main.send_request_recipient r
        """, nativeQuery = true)
    ProviderStatsRow findProviderStats();
}
