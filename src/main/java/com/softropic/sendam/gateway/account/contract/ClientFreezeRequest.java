package com.softropic.sendam.gateway.account.contract;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/admin/clients/{clientId}/freeze.
 */
public record ClientFreezeRequest(
        @NotBlank(message = "freeze_reason|Freeze reason is required") String reason
) {}
