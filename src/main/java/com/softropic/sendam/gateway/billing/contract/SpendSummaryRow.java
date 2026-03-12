package com.softropic.sendam.gateway.billing.contract;

public interface SpendSummaryRow {
    long getSmsDebit();
    long getSmsRefund();
    long getTopupApproved();
    long getSmsReservation();
    long getNetCreditsConsumed();
}
