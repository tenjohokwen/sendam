package com.softropic.sendam.gateway.provider.nexah.api;

import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrPayload;
import com.softropic.sendam.gateway.provider.nexah.contract.NexahDrResponse;
import com.softropic.sendam.gateway.provider.nexah.service.DrCallbackService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles inbound delivery report callbacks from Nexah.
 *
 * This endpoint is accessible without API key authentication — it is protected by
 * NexahSecurityConfiguration (@Order(0)) which permits /v1/provider/** unconditionally.
 *
 * Each DR entry advances the corresponding recipient's state machine (SUBMITTED -> COMPLETED/FAILED)
 * and triggers parent finalization + billing settlement when all recipients reach terminal state.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/provider")
public class DrCallbackResource {

    private final DrCallbackService drCallbackService;

    /**
     * Receives delivery report notifications from Nexah and processes them.
     *
     * @param payload the DR payload containing a list of delivery report entries
     * @return per-entry acknowledgement (status=1 for processed, status=0 for retry)
     */
    @PostMapping("/dr")
    public ResponseEntity<NexahDrResponse> handleDrCallback(@RequestBody NexahDrPayload payload) {
        log.info("DR callback received from Nexah: {} entries",
                payload.dlrList() != null ? payload.dlrList().size() : 0);
        NexahDrResponse response = drCallbackService.processDr(payload);
        return ResponseEntity.ok(response);
    }
}
