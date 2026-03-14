package com.softropic.sendam.gateway.audit.repo;

import com.softropic.sendam.gateway.audit.contract.AuditEventRow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    @Query(value = """
        SELECT
            ae.id           AS id,
            ae.event_type   AS eventType,
            ae.client_id    AS clientId,
            ae.actor        AS actor,
            ae.detail       AS detail,
            ae.occurred_at  AS occurredAt
        FROM main.audit_event ae
        WHERE (CAST(:clientId AS bigint) IS NULL OR ae.client_id = :clientId)
          AND (CAST(:from AS timestamptz) IS NULL OR ae.occurred_at >= :from)
          AND (CAST(:to   AS timestamptz) IS NULL OR ae.occurred_at <= :to)
        ORDER BY ae.occurred_at DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM main.audit_event ae
        WHERE (CAST(:clientId AS bigint) IS NULL OR ae.client_id = :clientId)
          AND (CAST(:from AS timestamptz) IS NULL OR ae.occurred_at >= :from)
          AND (CAST(:to   AS timestamptz) IS NULL OR ae.occurred_at <= :to)
        """,
        nativeQuery = true)
    Page<AuditEventRow> findEvents(
        @Param("clientId") Long clientId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );
}
