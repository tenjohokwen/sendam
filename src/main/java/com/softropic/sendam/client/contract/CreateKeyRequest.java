package com.softropic.sendam.client.contract;

/**
 * Request body for API key creation. Label is optional.
 */
public record CreateKeyRequest(String label) {}
