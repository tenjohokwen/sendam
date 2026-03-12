package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.SpendSummaryResponse;
import com.softropic.sendam.gateway.billing.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Admin credit management and analysis endpoints.
 */
@RestController
@RequestMapping("/api/admin/credits")
@RequiredArgsConstructor
@Slf4j
public class AdminCreditResource {

    private final CreditService creditService;

    /**
     * Returns a summary of credit spend (debits, refunds, topups) with optional filters.
     * GET /api/admin/credits/summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpendSummaryResponse> getSpendSummary(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("Admin credit summary query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(creditService.getSpendSummary(clientId, from, to));
    }
}
