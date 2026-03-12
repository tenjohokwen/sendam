package com.softropic.sendam.gateway.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softropic.sendam.gateway.sms.contract.SendRequestStatus;
import com.softropic.sendam.gateway.sms.contract.SmsFinalisedEvent;
import com.softropic.sendam.gateway.webhook.repo.WebhookDeliveryRepository;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpoint;
import com.softropic.sendam.gateway.webhook.repo.WebhookEndpointRepository;
import com.softropic.sendam.common.persistence.EntityStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsFinalisedWebhookListenerTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;
    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SmsFinalisedWebhookListener listener;

    @Test
    @DisplayName("onSmsFinalized: success - creates webhook delivery rows for each active endpoint and recipient")
    void onSmsFinalized_success() {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(10L);
        endpoint.setUrl("https://client.com/wh");
        
        when(webhookEndpointRepository.findByClientIdAndStatus(eq(100L), eq(EntityStatus.ACTIVE)))
                .thenReturn(List.of(endpoint));

        SmsFinalisedEvent.RecipientSummary recipient = new SmsFinalisedEvent.RecipientSummary("237671234567", "gw-123", SendRequestStatus.COMPLETED);
        SmsFinalisedEvent event = new SmsFinalisedEvent(100L, "req-123", List.of(recipient), 42L, 1L);

        listener.onSmsFinalized(event);

        verify(webhookDeliveryRepository, times(1)).save(any());
    }
}
