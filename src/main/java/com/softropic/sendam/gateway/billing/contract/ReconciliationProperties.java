package com.softropic.sendam.gateway.billing.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the periodic balance reconciliation job.
 * Bound from YAML prefix "sendam.reconciliation".
 */
@ConfigurationProperties(prefix = "sendam.reconciliation")
public record ReconciliationProperties(int intervalMinutes) {
}
