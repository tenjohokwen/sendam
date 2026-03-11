package com.softropic.sendam.gateway.audit.contract;

public record DomainAuditEvent(
    AuditEventType eventType,
    Long clientId,   // nullable — null for events with no client target
    String actor,    // admin username from security context, or "client:{clientId}"
    String detail    // human-readable context; may be null
) {}
