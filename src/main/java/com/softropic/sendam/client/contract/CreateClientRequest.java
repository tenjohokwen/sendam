package com.softropic.sendam.client.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
    @NotBlank @Size(min = 2, max = 100) String name,
    @Size(max = 100) String keyLabel
) {}
