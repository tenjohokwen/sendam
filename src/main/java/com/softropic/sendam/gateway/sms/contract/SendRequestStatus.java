package com.softropic.sendam.gateway.sms.contract;

public enum SendRequestStatus {
    ACCEPTED,
    SENDING,     // Interim state when a node picks it up for dispatch
    SUSPENDED,   // Scheduled send temporarily suspended during account or platform freeze
    SUBMITTED,
    COMPLETED,
    FAILED,
    FINALIZED,
    FAIL_FINALIZED,
    CANCELLED
}
