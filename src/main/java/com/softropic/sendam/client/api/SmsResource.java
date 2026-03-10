package com.softropic.sendam.client.api;

import com.softropic.sendam.client.contract.MessageStatusResponse;
import com.softropic.sendam.client.contract.SendSmsRequest;
import com.softropic.sendam.client.contract.SendSmsResponse;
import com.softropic.sendam.client.service.SmsService;
import com.softropic.sendam.security.contract.util.RateLimited;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client-facing SMS endpoints. Protected by the /v1/** API key security chain (@Order(1)).
 * No additional @PreAuthorize needed — the filter chain authenticates via API key.
 */
@RestController
@RequestMapping("/v1/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsResource {

    private final SmsService smsService;

    /**
     * Accepts an SMS send request from the authenticated client.
     * POST /v1/sms/send
     *
     * <p>The @RateLimited aspect enforces 10 req/s per client (AUTH-05 first half).
     * N-token recipient rate limit (AUTH-05 second half) is enforced inside SmsService.
     */
    @PostMapping("/send")
    @RateLimited(key = "sms_send", capacity = 10, duration = 1, unit = TimeUnit.SECONDS)
    public ResponseEntity<SendSmsResponse> sendSms(@RequestBody @Valid SendSmsRequest request) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SendSmsResponse response = smsService.sendSms(clientId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns per-recipient delivery status for a previously submitted send request.
     * GET /v1/sms/status/{sendRequestId}
     */
    @GetMapping("/status/{sendRequestId}")
    public ResponseEntity<MessageStatusResponse> getStatus(
            @PathVariable String sendRequestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        MessageStatusResponse response = smsService.getStatus(clientId, sendRequestId, page, pageSize);
        return ResponseEntity.ok(response);
    }
}
