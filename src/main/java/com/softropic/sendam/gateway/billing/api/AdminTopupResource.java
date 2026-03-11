package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.TopupStatusResponse;
import com.softropic.sendam.gateway.billing.service.TopupService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin top-up management endpoints.
 * Restricted to ROLE_ADMIN both at the filter chain level (AppEndpoints.ADMIN_TOPUPS) and
 * via @PreAuthorize for belt-and-suspenders defence.
 */
@RestController
@RequestMapping("/api/admin/topups")
@RequiredArgsConstructor
@Slf4j
public class AdminTopupResource {

    private final TopupService topupService;

    /**
     * Approve a PENDING_APPROVAL top-up.
     * PUT /api/admin/topups/{topup_id}/approve
     * Credits the client's balance immediately via TOPUP_APPROVED ledger entry.
     * Returns 409 TOPUP_ALREADY_PROCESSED if already APPROVED or REJECTED.
     */
    @PutMapping("/{topup_id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopupStatusResponse> approve(@PathVariable("topup_id") String topupId) {
        return ResponseEntity.ok(topupService.approve(topupId));
    }

    /**
     * Reject a PENDING_APPROVAL top-up.
     * PUT /api/admin/topups/{topup_id}/reject
     * No ledger entry is written — balance is unchanged.
     * Returns 409 TOPUP_ALREADY_PROCESSED if already APPROVED or REJECTED.
     */
    @PutMapping("/{topup_id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopupStatusResponse> reject(@PathVariable("topup_id") String topupId) {
        return ResponseEntity.ok(topupService.reject(topupId));
    }
}
