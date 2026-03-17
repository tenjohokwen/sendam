package com.softropic.sendam.gateway.billing.contract;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for DELETE /api/admin/platform/freeze.
 */
public record PlatformUnfreezeRequest(
        @NotBlank(message = "resolution_note|Resolution note is required") String resolution
) {}
