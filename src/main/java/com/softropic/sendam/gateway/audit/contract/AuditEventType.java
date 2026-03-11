package com.softropic.sendam.gateway.audit.contract;

public enum AuditEventType {

    // AUDT-01: Admin actions on clients
    CLIENT_CREATED,
    TOPUP_APPROVED,
    TOPUP_REJECTED,
    ADMIN_API_KEY_CREATED,
    ADMIN_API_KEY_REVOKED,

    // AUDT-02: Client API key self-service
    CLIENT_API_KEY_CREATED,
    CLIENT_API_KEY_REVOKED,

    // AUDT-03: SMS send submissions
    SMS_SEND_SUBMITTED,

    // AUDT-04: Webhook config changes
    WEBHOOK_REGISTERED,
    WEBHOOK_UPDATED,
    WEBHOOK_DELETED
}
