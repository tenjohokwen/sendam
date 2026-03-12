package com.softropic.sendam.gateway.account.contract;

public record ClientCreatedEvent(
    Long clientId,
    String name,
    String keyLabel
) {}
