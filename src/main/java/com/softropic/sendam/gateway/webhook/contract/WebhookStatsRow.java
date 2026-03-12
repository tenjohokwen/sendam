package com.softropic.sendam.gateway.webhook.contract;

public interface WebhookStatsRow {
    long getTotalAttempts();
    long getFailureCount();
    long getExhaustedCount();
}
