package com.softropic.sendam.gateway.sms.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record SendSmsRequest(
    @NotBlank @Size(max = 200)
    @JsonProperty("sendRequestId") String sendRequestId,

    @NotBlank
    @JsonProperty("message") String message,

    @NotEmpty @Size(max = 1000)
    @JsonProperty("recipients") List<String> recipients,

    @JsonProperty("scheduleTime") Instant scheduleTime
) {}
