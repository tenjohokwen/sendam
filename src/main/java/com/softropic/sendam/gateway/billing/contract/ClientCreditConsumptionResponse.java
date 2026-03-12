package com.softropic.sendam.gateway.billing.contract;

import java.time.Instant;

public record ClientCreditConsumptionResponse(
    Long netCreditsConsumed,
    Instant from,
    Instant to
) {}
