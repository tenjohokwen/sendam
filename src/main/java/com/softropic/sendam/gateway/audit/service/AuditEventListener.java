package com.softropic.sendam.gateway.audit.service;

import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditEventService auditEventService;

    @EventListener
    public void handle(DomainAuditEvent event) {
        try {
            auditEventService.record(event);
        } catch (Exception e) {
            // Audit failure must NEVER propagate to the caller — log and swallow.
            // The core operation succeeded; losing an audit row is acceptable.
            log.error("Failed to record audit event type={} clientId={} actor={}",
                      event.eventType(), event.clientId(), event.actor(), e);
        }
    }
}
