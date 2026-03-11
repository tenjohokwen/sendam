package com.softropic.sendam.gateway.sms.api;

import com.softropic.sendam.gateway.sms.contract.CancelSmsResponse;
import com.softropic.sendam.gateway.sms.contract.MessageStatusResponse;
import com.softropic.sendam.gateway.sms.contract.SendSmsRequest;
import com.softropic.sendam.gateway.sms.contract.SendSmsResponse;
import com.softropic.sendam.gateway.sms.service.SmsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * <p>Rate limiting (10 req/s and 1000 recipients/min per AUTH-05) is enforced
     * inside SmsService — the authoritative boundary regardless of caller.
     */
    @PostMapping("/send")
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

    /**
     * Cancels an ACCEPTED scheduled SMS request and releases the reserved credits.
     * DELETE /v1/sms/scheduled/{sendRequestId}
     *
     * <p>Returns 200 CANCELLED when successful. Returns 409 CANCEL_NOT_ALLOWED if
     * the request is not in ACCEPTED status or is not a scheduled request.
     * Not rate-limited per v8 contract.
     */
    @DeleteMapping("/scheduled/{sendRequestId}")
    public ResponseEntity<CancelSmsResponse> cancelScheduled(@PathVariable String sendRequestId) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        CancelSmsResponse response = smsService.cancelScheduled(clientId, sendRequestId);
        return ResponseEntity.ok(response);
    }
}
