package com.softropic.sendam.gateway.analytics.contract;

public interface DeliveryStatRow {
    long getTotalSent();
    long getDelivered();
    long getFailed();
    long getTotalSegments();
}
