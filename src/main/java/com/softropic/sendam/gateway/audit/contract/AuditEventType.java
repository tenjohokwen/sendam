package com.softropic.sendam.gateway.audit.contract;

public enum AuditEventType {

    // AUDT-01: Admin actions on clients
    CLIENT_CREATED,
    TOPUP_APPROVED,
    TOPUP_REJECTED,
    // PLAT-01: Admin records a Nexah credit purchase
    NEXAH_PURCHASE_RECORDED,
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
    WEBHOOK_DELETED,

    // CFREEZE-03: Admin freeze/unfreeze client account
    CLIENT_ACCOUNT_FROZEN,
    CLIENT_ACCOUNT_UNFROZEN,
    // PFLAT-03: Admin freeze/unfreeze platform
    PLATFORM_FROZEN,
    PLATFORM_UNFROZEN,

    // SEGDEV-02: non-zero segment deviation detected
    SEGMENT_DEVIATION,
    // BOOK-06: platform frozen due to unrecovered shortfall
    PLATFORM_FREEZE_SHORTFALL,
    // BALREC-03: balance reconciliation mismatch detected
    BALANCE_DEVIATION,

    // DEVMGMT-03: admin acknowledges a deviation alert
    DEVIATION_ALERT_ACKNOWLEDGED,
    // DEVMGMT-04: admin resolves a deviation alert
    DEVIATION_ALERT_RESOLVED
}
