package com.softropic.sendam.client.api;

import com.softropic.sendam.client.contract.CreateTopupRequest;
import com.softropic.sendam.client.contract.CreateTopupResponse;
import com.softropic.sendam.client.contract.TopupStatusResponse;
import com.softropic.sendam.client.service.TopupService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client-facing top-up endpoints. Protected by the /v1/** API key security chain (@Order(1)).
 * No @PreAuthorize needed — the filter chain authenticates via API key.
 *
 * <p>NOTE: v8 contract uses 200 OK for top-up creation, not 201 Created.
 */
@RestController
@RequestMapping("/v1/credits/topups")
@RequiredArgsConstructor
@Slf4j
public class TopupResource {

    private final TopupService topupService;

    /**
     * Submit a new top-up request.
     * POST /v1/credits/topups
     * Returns 200 OK with topup_id in "top_NNN" format and status PENDING_APPROVAL.
     */
    @PostMapping
    public ResponseEntity<CreateTopupResponse> createTopup(@Valid @RequestBody CreateTopupRequest request) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(topupService.createTopup(clientId, request));
    }

    /**
     * Query the current status of a top-up request.
     * GET /v1/credits/topups/{topup_id}
     * Only returns top-ups belonging to the authenticated client.
     */
    @GetMapping("/{topup_id}")
    public ResponseEntity<TopupStatusResponse> getTopupStatus(@PathVariable("topup_id") String topupId) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(topupService.getTopupStatus(clientId, topupId));
    }
}
