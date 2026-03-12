package com.softropic.sendam.gateway.sms.contract;

import java.time.Instant;

public record ScheduledSmsRow(
    Long id,
    Long clientId,
    String sendRequestId,
    String sender,
    int messageCount,
    Instant scheduleTime,
    long reservedCredits
) {}
