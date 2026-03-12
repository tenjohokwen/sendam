package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.account.contract.ClientCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingInitializationListener {

    private final CreditService creditService;

    @EventListener
    public void onClientCreated(ClientCreatedEvent event) {
        log.info("Initializing billing balance for new client: clientId={}", event.clientId());
        creditService.initializeBalance(event.clientId());
    }
}
