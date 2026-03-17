package com.softropic.sendam.gateway.billing.config;

import com.softropic.sendam.gateway.billing.contract.ReconciliationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the billing module.
 *
 * Registers ReconciliationProperties for binding from sendam.reconciliation.* YAML keys.
 * Does NOT add @EnableScheduling — scheduling is already enabled by ClientConfig
 * in the sms.config package; duplicate @EnableScheduling adds confusion without benefit.
 */
@Configuration
@EnableConfigurationProperties(ReconciliationProperties.class)
public class BillingConfig {
}
