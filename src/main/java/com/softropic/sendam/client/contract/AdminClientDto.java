package com.softropic.sendam.client.contract;

import com.softropic.sendam.common.persistence.EntityStatus;

/**
 * Single client row for GET /api/admin/clients.
 * balance is the current available credit balance derived from the credit ledger.
 */
public record AdminClientDto(
        Long id,
        String name,
        EntityStatus status,
        long balance
) {
}
