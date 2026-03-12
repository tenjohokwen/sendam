package com.softropic.sendam.gateway.webhook.service;

import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryRow;
import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.gateway.webhook.contract.WebhookEndpointRow;
import com.softropic.sendam.gateway.webhook.repo.WebhookDeliveryRepository;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpointRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminWebhookMonitorService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    public Page<WebhookEndpointRow> getEndpoints(Pageable pageable) {
        return endpointRepository.findAll(pageable)
            .map(e -> new WebhookEndpointRow(
                e.getId(),
                e.getClientId(),
                e.getPublicId(),
                e.getUrl(),
                e.getEvents(),
                e.getStatus().name(),
                e.getCreatedDate()
            ));
    }

    public Page<WebhookDeliveryRow> getDeliveries(Long clientId,
                                                   WebhookDeliveryStatus attemptStatus,
                                                   Pageable pageable) {
        return deliveryRepository.findByFilters(clientId, attemptStatus, pageable)
            .map(d -> new WebhookDeliveryRow(
                d.getId(),
                d.getClientId(),
                d.getSendRequestId(),
                d.getRecipient(),
                d.getDeliveryStatus(),
                d.getAttemptStatus().name(),
                d.getAttemptCount(),
                d.getLastAttemptAt(),
                d.getNextAttemptAt(),
                d.getHttpStatus()
            ));
    }
}
