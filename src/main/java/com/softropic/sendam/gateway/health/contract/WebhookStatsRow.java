package com.softropic.sendam.gateway.health.contract;

public interface WebhookStatsRow {
    long getTotalAttempts();
    long getFailureCount();
    long getExhaustedCount();
}
