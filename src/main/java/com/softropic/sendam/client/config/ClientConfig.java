package com.softropic.sendam.client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task infrastructure for the client module.
 * Required for SmsSchedulerService's @Scheduled poller to run.
 */
@Configuration
@EnableScheduling
public class ClientConfig {
}
