package com.softropic.sendam.gateway.billing.contract;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/admin/platform/freeze.
 */
public record PlatformFreezeRequest(
        @NotBlank(message = "freeze_reason|Freeze reason is required") String reason
) {}
