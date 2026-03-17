package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.PlatformFreezeRequest;
import com.softropic.sendam.gateway.billing.contract.PlatformFreezeResponse;
import com.softropic.sendam.gateway.billing.contract.PlatformUnfreezeRequest;
import com.softropic.sendam.gateway.billing.service.PlatformFreezeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin platform freeze/unfreeze endpoints.
 * Restricted to ROLE_ADMIN both at the filter chain level (AppEndpoints.ADMIN_PLATFORM_FREEZE)
 * and via @PreAuthorize for belt-and-suspenders defence.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>POST   /api/admin/platform/freeze — freeze the entire platform</li>
 *   <li>DELETE /api/admin/platform/freeze — lift the platform freeze</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/platform")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformFreezeResource {

    private final PlatformFreezeService platformFreezeService;

    /**
     * Freezes the platform. All scheduled SMS across all clients are suspended atomically.
     * POST body: {"reason": "Critical shortfall — operator action required"}
     * Returns 200 with current freeze state.
     *
     * @param request freeze request containing mandatory reason
     * @return 200 OK with frozen=true
     */
    @PostMapping("/freeze")
    public ResponseEntity<PlatformFreezeResponse> freezePlatform(
            @Valid @RequestBody PlatformFreezeRequest request) {
        platformFreezeService.freeze(request.reason(), null);
        log.warn("Admin initiated platform freeze: reason={}", request.reason());
        return ResponseEntity.ok(new PlatformFreezeResponse(true, "Platform frozen"));
    }

    /**
     * Lifts the platform freeze. All suspended SMS across all clients are resumed atomically.
     * DELETE body: {"resolution": "Nexah balance restored, shortfall accounted"}
     * Returns 200 with current freeze state.
     *
     * @param request unfreeze request containing mandatory resolution note
     * @return 200 OK with frozen=false
     */
    @DeleteMapping("/freeze")
    public ResponseEntity<PlatformFreezeResponse> unfreezePlatform(
            @Valid @RequestBody PlatformUnfreezeRequest request) {
        platformFreezeService.unfreeze(request.resolution());
        log.info("Admin lifted platform freeze: resolution={}", request.resolution());
        return ResponseEntity.ok(new PlatformFreezeResponse(false, "Platform unfrozen"));
    }
}
