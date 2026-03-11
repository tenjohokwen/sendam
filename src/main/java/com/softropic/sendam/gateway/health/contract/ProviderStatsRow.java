package com.softropic.sendam.gateway.health.contract;

public interface ProviderStatsRow {
    long getTotalSubmitted();
    long getDrReceived();
    long getFailedCount();
}
