package com.softropic.sendam.client.contract;

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
