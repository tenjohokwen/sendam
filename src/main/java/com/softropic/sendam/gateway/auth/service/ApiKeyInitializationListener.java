package com.softropic.sendam.gateway.auth.service;

import com.softropic.sendam.gateway.account.contract.ClientCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyInitializationListener {

    private final ApiKeyService apiKeyService;

    @EventListener
    public void onClientCreated(ClientCreatedEvent event) {
        log.info("Initializing API key for new client: clientId={}, name={}", event.clientId(), event.name());
        apiKeyService.generateAndPersist(event.clientId(), event.keyLabel());
    }
}
