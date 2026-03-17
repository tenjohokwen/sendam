package com.softropic.sendam.gateway.sms.contract;

public enum SendRequestStatus {
    ACCEPTED,
    SUSPENDED,   // Scheduled send temporarily suspended during account or platform freeze
    SUBMITTED,
    COMPLETED,
    FAILED,
    FINALIZED,
    FAIL_FINALIZED,
    CANCELLED
}
