package com.softropic.sendam.gateway.billing.contract;

import java.time.Instant;
import java.util.List;

public record DeviationAlertDto(
    Long id,
    DeviationAlertType type,
    AlertStatus alertStatus,
    long delta,
    Instant createdDate,
    // --- SEGMENT / PLATFORM_FREEZE fields (null for BALANCE) ---
    String sendRequestRef,
    Long clientId,
    Long shortfallAmount,
    Long unrecoveredAmount,
    String financialAction,
    Boolean clientFrozen,
    Boolean platformFrozen,
    List<RecipientBreakdownDto> perRecipientBreakdown,  // null except on detail fetch for SEGMENT type
    // --- BALANCE fields (null for SEGMENT / PLATFORM_FREEZE) ---
    Long nexahBalance,
    Long sendamBalance,
    // --- audit trail (null on list endpoint, populated on detail fetch) ---
    List<DeviationAlertEventDto> auditTrail
) {
    /**
     * Inner DTO for per-recipient segment deviation breakdown.
     * Maps from SegmentDeviationAlert.RecipientDeviationEntry.
     */
    public record RecipientBreakdownDto(
        String recipient,
        int expectedSegments,
        int actualSegments,
        int delta
    ) {}
}
