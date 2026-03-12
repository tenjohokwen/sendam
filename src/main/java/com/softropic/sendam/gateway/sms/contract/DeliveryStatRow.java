package com.softropic.sendam.gateway.sms.contract;

public interface DeliveryStatRow {
    long getTotalSent();
    long getDelivered();
    long getFailed();
    long getTotalSegments();
}
