package com.softropic.sendam.gateway.billing.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviationAlertNoteRequest(
    @NotBlank @Size(max = 500) String note
) {}
