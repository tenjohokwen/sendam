package com.softropic.sendam.gateway.analytics.repo;

import com.softropic.sendam.gateway.analytics.contract.DeliveryStatRow;
import com.softropic.sendam.gateway.analytics.contract.DeliveryDailyStatRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

@org.springframework.stereotype.Repository
public interface DeliveryAnalyticsRepository extends Repository<Object, Long> {

    @Query(value = """
        SELECT
            COUNT(*)                                               AS total_sent,
            COUNT(CASE WHEN r.send_status = 'FINALIZED'
                       THEN 1 END)                                AS delivered,
            COUNT(CASE WHEN r.send_status = 'FAIL_FINALIZED'
                       THEN 1 END)                                AS failed,
            COALESCE(SUM(r.segments_consumed), 0)                 AS total_segments
        FROM main.send_request_recipient r
        WHERE (:clientId IS NULL OR r.client_id = :clientId)
          AND (:from IS NULL    OR r.created_date >= :from)
          AND (:to   IS NULL    OR r.created_date <= :to)
        """, nativeQuery = true)
    DeliveryStatRow findDeliveryStats(
        @Param("clientId") Long clientId,
        @Param("from")     Instant from,
        @Param("to")       Instant to
    );

    @Query(value = """
        SELECT
            DATE_TRUNC('day', r.created_date)::date               AS day,
            COUNT(*)                                               AS total_sent,
            COUNT(CASE WHEN r.send_status = 'FINALIZED'
                       THEN 1 END)                                 AS delivered,
            COUNT(CASE WHEN r.send_status = 'FAIL_FINALIZED'
                       THEN 1 END)                                 AS failed,
            COALESCE(SUM(r.segments_consumed), 0)                  AS total_segments
        FROM main.send_request_recipient r
        WHERE (:clientId IS NULL OR r.client_id = :clientId)
          AND (:from IS NULL    OR r.created_date >= :from)
          AND (:to   IS NULL    OR r.created_date <= :to)
        GROUP BY DATE_TRUNC('day', r.created_date)::date
        ORDER BY day
        """, nativeQuery = true)
    List<DeliveryDailyStatRow> findDailyBreakdown(
        @Param("clientId") Long clientId,
        @Param("from")     Instant from,
        @Param("to")       Instant to
    );
}
