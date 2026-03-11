package com.softropic.sendam.gateway.spend.contract;

import java.sql.Timestamp;

public interface TopupHistoryRow {
    Long getId();
    Long getClientId();
    long getAmount();
    String getTransactionId();
    String getPaymentType();
    String getAccountNumber();
    String getTopupStatus();
    Timestamp getCreatedDate();
    Timestamp getApprovedAt();   // nullable
    Timestamp getRejectedAt();   // nullable
}
