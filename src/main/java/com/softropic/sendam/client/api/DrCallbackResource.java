package com.softropic.sendam.client.api;

import com.softropic.sendam.client.contract.nexah.NexahDrPayload;
import com.softropic.sendam.client.contract.nexah.NexahDrResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Handles inbound delivery report callbacks from Nexah.
 *
 * This endpoint is accessible without API key authentication — it is protected by
 * NexahSecurityConfiguration (@Order(0)) which permits /v1/provider/** unconditionally.
 *
 * STUB: Plan 04-01 — logs the payload and returns an empty acknowledgement.
 * Plan 04-02 will wire DrCallbackService to replace the stub with real state machine processing.
 */
@Slf4j
@RestController
@RequestMapping("/v1/provider")
public class DrCallbackResource {

    /**
     * Receives delivery report notifications from Nexah.
     *
     * @param payload the DR payload containing a list of delivery report entries
     * @return empty dlrlist acknowledgement (stub — processing wired in Plan 04-02)
     */
    @PostMapping("/dr")
    public ResponseEntity<NexahDrResponse> handleDrCallback(@RequestBody NexahDrPayload payload) {
        log.info("DR callback received from Nexah: {} entries",
                payload.dlrList() != null ? payload.dlrList().size() : 0);
        // STUB: Plan 04-02 replaces this with DrCallbackService.process(payload)
        return ResponseEntity.ok(new NexahDrResponse(List.of()));
    }
}
