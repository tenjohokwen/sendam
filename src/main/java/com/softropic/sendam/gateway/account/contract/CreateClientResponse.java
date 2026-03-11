package com.softropic.sendam.gateway.account.contract;

public record CreateClientResponse(
    Long clientId,
    Long apiKeyId,
    String rawApiKey   // shown ONCE — never stored
) {}
