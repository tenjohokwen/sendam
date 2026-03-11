package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.BalanceResponse;
import com.softropic.sendam.gateway.billing.contract.LedgerHistoryResponse;
import com.softropic.sendam.gateway.billing.service.CreditService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client-facing credit endpoints. Protected by the /v1/** API key security chain (@Order(1)).
 * No additional @PreAuthorize needed — the filter chain authenticates via API key.
 */
@RestController
@RequestMapping("/v1/credits")
@RequiredArgsConstructor
@Slf4j
public class CreditResource {

    private final CreditService creditService;

    /**
     * Returns the current credit balance for the authenticated client.
     * GET /v1/credits/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance() {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(creditService.getBalance(clientId));
    }

    /**
     * Returns paginated ledger history for the authenticated client, sorted newest-first.
     * GET /v1/credits/ledger
     */
    @GetMapping("/ledger")
    public ResponseEntity<LedgerHistoryResponse> getLedgerHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(creditService.getLedgerHistory(clientId, page, size));
    }
}
