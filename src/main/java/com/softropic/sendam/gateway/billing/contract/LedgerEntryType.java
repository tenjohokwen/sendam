package com.softropic.sendam.gateway.billing.contract;

public enum LedgerEntryType {
    TOPUP_PENDING,
    TOPUP_APPROVED,
    SMS_RESERVATION,
    SMS_DEBIT,
    SMS_REFUND,
    // BOOK-04/05/06: extra client debit beyond the buffered reservation
    SMS_EXTRA_DEBIT
}
