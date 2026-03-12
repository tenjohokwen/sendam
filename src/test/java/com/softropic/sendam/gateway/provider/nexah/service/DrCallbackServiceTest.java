package com.softropic.sendam.gateway.provider.nexah.service;

import com.softropic.sendam.gateway.provider.nexah.contract.*;
import com.softropic.sendam.gateway.sms.contract.ProviderDeliveryReportEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrCallbackServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DrCallbackService drCallbackService;

    @Test
    @DisplayName("processDr: success - parses payload and publishes ProviderDeliveryReportEvent")
    void processDr_success() {
        NexahDrEntry entry = new NexahDrEntry("1", "Success", "237671234567", "gw-123", "2", "t1", "t2", "t3", "DELIVRD", "traff");
        NexahDrPayload payload = new NexahDrPayload(List.of(entry));

        NexahDrResponse response = drCallbackService.processDr(payload);

        assertThat(response.dlrList()).hasSize(1);
        assertThat(response.dlrList().get(0).status()).isEqualTo(1);
        
        verify(eventPublisher).publishEvent(any(ProviderDeliveryReportEvent.class));
    }

    @Test
    @DisplayName("processDr: empty - returns empty response without publishing event")
    void processDr_empty() {
        NexahDrPayload payload = new NexahDrPayload(List.of());

        NexahDrResponse response = drCallbackService.processDr(payload);

        assertThat(response.dlrList()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
