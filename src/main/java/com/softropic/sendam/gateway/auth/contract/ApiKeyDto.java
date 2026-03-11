package com.softropic.sendam.gateway.auth.contract;

import java.time.Instant;

/**
 * Response DTO for API key listings. Never includes the raw key value.
 */
public record ApiKeyDto(
    Long id,
    String label,
    String status,
    Instant createdDate
) {}
