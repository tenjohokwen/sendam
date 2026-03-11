package com.softropic.sendam.gateway.auth.contract;

/**
 * Request body for API key creation. Label is optional.
 */
public record CreateKeyRequest(String label) {}
