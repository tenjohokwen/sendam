package com.softropic.sendam.gateway.billing.contract;

import java.time.Instant;

public record DeviationAlertEventDto(
    Long id,
    AlertStatus previousStatus,
    AlertStatus newStatus,
    String adminNote,
    String actedBy,
    Instant actedAt
) {}
