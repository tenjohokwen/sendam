package com.softropic.sendam.gateway.account.contract;

/**
 * Response body for PUT /api/admin/clients/{clientId}/freeze and /unfreeze.
 */
public record ClientFreezeResponse(
        Long clientId,
        boolean frozen,
        String message
) {}
