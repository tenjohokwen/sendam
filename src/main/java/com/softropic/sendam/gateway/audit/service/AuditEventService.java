package com.softropic.sendam.gateway.audit.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventRow;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.audit.repo.AuditEventEntity;
import com.softropic.sendam.gateway.audit.repo.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Writes one audit row in its own transaction, isolated from the caller's transaction.
     * REQUIRES_NEW: if the outer transaction rolls back, this write is already committed
     * (audit rows survive the failure, recording that the operation was attempted).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(DomainAuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setEventType(event.eventType().name());
        entity.setClientId(event.clientId());
        entity.setActor(event.actor());
        entity.setDetail(event.detail());
        entity.setOccurredAt(Instant.now());
        auditEventRepository.save(entity);
        log.debug("Audit event recorded: type={} clientId={} actor={}",
                  event.eventType(), event.clientId(), event.actor());
    }

    /**
     * Returns a paginated page of audit events, optionally filtered by clientId and time range.
     * All params are nullable — null means "no filter on that dimension".
     */
    @Transactional(readOnly = true)
    public Page<AuditEventRow> findEvents(Long clientId, Instant from, Instant to, Pageable pageable) {
        return auditEventRepository.findEvents(clientId, from, to, pageable);
    }
}
