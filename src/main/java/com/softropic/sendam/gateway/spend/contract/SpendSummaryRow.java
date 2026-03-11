package com.softropic.sendam.gateway.spend.contract;

public interface SpendSummaryRow {
    long getSmsDebit();
    long getSmsRefund();
    long getTopupApproved();
    long getSmsReservation();
    long getNetCreditsConsumed();
}
