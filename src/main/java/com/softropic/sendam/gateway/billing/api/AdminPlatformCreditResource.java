package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.PlatformBalanceResponse;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
import com.softropic.sendam.gateway.billing.contract.PlatformLedgerHistoryResponse;
import com.softropic.sendam.gateway.billing.contract.RecordNexahPurchaseRequest;
import com.softropic.sendam.gateway.billing.contract.RecordNexahPurchaseResponse;
import com.softropic.sendam.gateway.billing.service.PlatformCreditService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin platform credit management endpoints.
 * Restricted to ROLE_ADMIN both at the filter chain level (AppEndpoints.ADMIN_PLATFORM_CREDITS) and
 * via @PreAuthorize for belt-and-suspenders defence.
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>POST /api/admin/platform/credits/purchases — record a Nexah credit purchase</li>
 *   <li>GET  /api/admin/platform/credits/balance   — query current platform balance</li>
 *   <li>GET  /api/admin/platform/credits/ledger    — browse paginated ledger history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/platform/credits")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformCreditResource {

    private final PlatformCreditService platformCreditService;

    /**
     * Record a Nexah credit purchase — increases the platform balance by the given amount.
     * Called by the operator after purchasing SMS credits from Nexah.
     * POST /api/admin/platform/credits/purchases
     */
    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public RecordNexahPurchaseResponse recordPurchase(@Valid @RequestBody RecordNexahPurchaseRequest request) {
        log.info("Admin recording Nexah purchase: amount={}", request.amount());
        return platformCreditService.recordNexahPurchase(request);
    }

    /**
     * Query the current platform balance without acquiring a lock.
     * GET /api/admin/platform/credits/balance
     */
    @GetMapping("/balance")
    public PlatformBalanceResponse getBalance() {
        return platformCreditService.getBalance();
    }

    /**
     * Browse platform ledger history, optionally filtered by entry type.
     * GET /api/admin/platform/credits/ledger
     *
     * @param type optional filter — when omitted all entries are returned
     * @param page 0-based page index (default 0)
     * @param size page size (default 50, capped at 200 in service layer)
     */
    @GetMapping("/ledger")
    public PlatformLedgerHistoryResponse getLedgerHistory(
            @RequestParam(required = false) PlatformLedgerEntryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return platformCreditService.getLedgerHistory(type, page, size);
    }
}
