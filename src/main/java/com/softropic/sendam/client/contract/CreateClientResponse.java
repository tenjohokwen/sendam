package com.softropic.sendam.client.contract;

public record CreateClientResponse(
    Long clientId,
    Long apiKeyId,
    String rawApiKey   // shown ONCE — never stored
) {}
