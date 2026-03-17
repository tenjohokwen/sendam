package com.softropic.sendam.gateway.billing.contract;

/**
 * Response body for POST /api/admin/platform/freeze and DELETE /api/admin/platform/freeze.
 */
public record PlatformFreezeResponse(
        boolean frozen,
        String message
) {}
