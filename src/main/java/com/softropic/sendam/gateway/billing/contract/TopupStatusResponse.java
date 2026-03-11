package com.softropic.sendam.gateway.billing.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response for GET /v1/credits/topups/{topup_id} and admin approve/reject endpoints.
 * approved_at is null when status is PENDING_APPROVAL or REJECTED.
 */
public record TopupStatusResponse(
        @JsonProperty("topup_id") String topupId,
        long amount,
        TopupStatus status,
        @JsonProperty("approved_at") Instant approvedAt
) {
}
