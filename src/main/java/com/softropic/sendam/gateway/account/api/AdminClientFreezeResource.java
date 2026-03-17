package com.softropic.sendam.gateway.account.api;

import com.softropic.sendam.gateway.account.contract.ClientFreezeRequest;
import com.softropic.sendam.gateway.account.contract.ClientFreezeResponse;
import com.softropic.sendam.gateway.account.contract.ClientUnfreezeRequest;
import com.softropic.sendam.gateway.account.service.ClientFreezeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin client freeze/unfreeze endpoints.
 * Restricted to ROLE_ADMIN both at the filter chain level (AppEndpoints.ADMIN_CLIENT_FREEZE)
 * and via @PreAuthorize for belt-and-suspenders defence.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>PUT /api/admin/clients/{clientId}/freeze   — freeze a client account</li>
 *   <li>PUT /api/admin/clients/{clientId}/unfreeze — lift a client account freeze</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/clients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminClientFreezeResource {

    private final ClientFreezeService clientFreezeService;

    /**
     * Freezes a client account. Suspended scheduled SMS are paused atomically.
     * PUT body: {"reason": "Shortfall detected"}
     * Returns 200 with current freeze state.
     *
     * @param clientId client to freeze
     * @param request  freeze request containing mandatory reason
     * @return 200 OK with frozen=true
     */
    @PutMapping("/{clientId}/freeze")
    public ResponseEntity<ClientFreezeResponse> freezeClient(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientFreezeRequest request) {
        clientFreezeService.freeze(clientId, request.reason());
        log.info("Admin froze client {}: reason={}", clientId, request.reason());
        return ResponseEntity.ok(new ClientFreezeResponse(clientId, true, "Client account frozen"));
    }

    /**
     * Unfreezes a client account. Suspended scheduled SMS are resumed atomically.
     * PUT body: {"resolution": "Balance restored manually"}
     * Returns 200 with current freeze state.
     *
     * @param clientId client to unfreeze
     * @param request  unfreeze request containing mandatory resolution note
     * @return 200 OK with frozen=false
     */
    @PutMapping("/{clientId}/unfreeze")
    public ResponseEntity<ClientFreezeResponse> unfreezeClient(
            @PathVariable Long clientId,
            @Valid @RequestBody ClientUnfreezeRequest request) {
        clientFreezeService.unfreeze(clientId, request.resolution());
        log.info("Admin unfroze client {}: resolution={}", clientId, request.resolution());
        return ResponseEntity.ok(new ClientFreezeResponse(clientId, false, "Client account unfrozen"));
    }
}
