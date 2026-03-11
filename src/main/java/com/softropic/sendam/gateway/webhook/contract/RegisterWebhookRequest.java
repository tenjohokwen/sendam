package com.softropic.sendam.gateway.webhook.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RegisterWebhookRequest(
        @NotBlank String url,
        @NotNull List<String> events
) {}
