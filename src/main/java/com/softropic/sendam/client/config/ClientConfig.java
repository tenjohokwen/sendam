package com.softropic.sendam.client.config;

import com.softropic.sendam.client.contract.nexah.NexahProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task infrastructure for the client module.
 * Required for SmsSchedulerService's @Scheduled poller to run.
 * Also enables NexahProperties binding from application.yaml nexah.* prefix.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NexahProperties.class)
public class ClientConfig {
}
