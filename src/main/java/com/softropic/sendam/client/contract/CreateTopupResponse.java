package com.softropic.sendam.client.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response for POST /v1/credits/topups.
 * Returns the assigned topup_id (in "top_NNN" format), initial PENDING_APPROVAL status,
 * and the timestamp when the request was recorded.
 */
public record CreateTopupResponse(
        @JsonProperty("topup_id") String topupId,
        TopupStatus status,
        @JsonProperty("requested_at") Instant requestedAt
) {
}
