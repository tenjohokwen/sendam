package com.softropic.sendam.gateway.webhook.service;

import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookRequest;
import com.softropic.sendam.gateway.webhook.contract.RegisterWebhookResponse;
import com.softropic.sendam.gateway.webhook.contract.WebhookDeliveryStatus;
import com.softropic.sendam.gateway.webhook.repo.WebhookDelivery;
import com.softropic.sendam.gateway.webhook.repo.WebhookDeliveryRepository;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpoint;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpointRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.springframework.context.ApplicationEventPublisher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;
    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private RestTemplate webhookRestTemplate;

    @InjectMocks
    private WebhookService webhookService;

    @Test
    @DisplayName("register: success - creates new endpoint if none exists")
    void register_createNew() {
        RegisterWebhookRequest request = new RegisterWebhookRequest("https://example.com/callback", List.of("sms.finalized"));
        when(webhookEndpointRepository.findByClientId(1L)).thenReturn(Optional.empty());
        when(webhookEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterWebhookResponse response = webhookService.register(1L, request);

        assertThat(response.webhookId()).startsWith("wh_");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(webhookEndpointRepository).save(argThat(e -> e.getUrl().equals("https://example.com/callback")));
    }

    @Test
    @DisplayName("dispatchPendingDeliveries: success - dispatches due delivery and updates status")
    void dispatchPendingDeliveries_success() {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(1L)
                .webhookEndpointId(10L)
                .payload("{\"event\":\"test\"}")
                .attemptStatus(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .build();
        
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(10L);
        endpoint.setUrl("https://client.com/wh");
        endpoint.setStatus(EntityStatus.ACTIVE);

        when(webhookDeliveryRepository.findByAttemptStatusAndNextAttemptAtBefore(eq(WebhookDeliveryStatus.PENDING), any()))
                .thenReturn(List.of(delivery));
        when(webhookEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        webhookService.dispatchPendingDeliveries();

        assertThat(delivery.getAttemptStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getHttpStatus()).isEqualTo(200);
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("dispatchPendingDeliveries: failure - applies backoff when HTTP call fails")
    void dispatchPendingDeliveries_failure_backoff() {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(1L)
                .webhookEndpointId(10L)
                .payload("{}")
                .attemptStatus(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .build();
        
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(10L);
        endpoint.setUrl("https://client.com/wh");
        endpoint.setStatus(EntityStatus.ACTIVE);

        when(webhookDeliveryRepository.findByAttemptStatusAndNextAttemptAtBefore(any(), any()))
                .thenReturn(List.of(delivery));
        when(webhookEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(webhookRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR));

        webhookService.dispatchPendingDeliveries();

        assertThat(delivery.getAttemptStatus()).isEqualTo(WebhookDeliveryStatus.PENDING); // stays pending for next retry
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(Instant.now());
        verify(webhookDeliveryRepository).save(delivery);
    }
}
