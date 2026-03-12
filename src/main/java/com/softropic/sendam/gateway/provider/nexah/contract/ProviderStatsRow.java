package com.softropic.sendam.gateway.provider.nexah.contract;

public interface ProviderStatsRow {
    long getTotalSubmitted();
    long getDrReceived();
    long getFailedCount();
}
