package com.softropic.sendam.client.repo;

import com.softropic.sendam.common.persistence.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    /**
     * Finds the webhook endpoint registered for a given client (at most one in v1).
     */
    Optional<WebhookEndpoint> findByClientId(Long clientId);

    /**
     * Finds all webhook endpoints for a client filtered by entity lifecycle status.
     * Used by the listener to load ACTIVE endpoints before creating delivery rows.
     */
    List<WebhookEndpoint> findByClientIdAndStatus(Long clientId, EntityStatus status);
}
