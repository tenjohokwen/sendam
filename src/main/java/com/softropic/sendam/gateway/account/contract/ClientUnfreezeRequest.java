package com.softropic.sendam.gateway.account.contract;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/admin/clients/{clientId}/unfreeze.
 */
public record ClientUnfreezeRequest(
        @NotBlank(message = "resolution_note|Resolution note is required") String resolution
) {}
