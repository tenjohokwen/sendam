package com.softropic.sendam.gateway.audit.contract;

import java.time.Instant;

public interface AuditEventRow {
    Long getId();
    String getEventType();
    Long getClientId();
    String getActor();
    String getDetail();
    Instant getOccurredAt();
}
