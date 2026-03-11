package com.softropic.sendam.gateway.spend.api;

import com.softropic.sendam.gateway.spend.contract.SpendSummaryResponse;
import com.softropic.sendam.gateway.spend.contract.TopupHistoryResponse;
import com.softropic.sendam.gateway.spend.service.SpendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/spend")
@RequiredArgsConstructor
@Slf4j
public class AdminSpendResource {

    private final SpendService spendService;

    @GetMapping("/credits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpendSummaryResponse> getSpendSummary(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("Admin spend summary query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(spendService.getSpendSummary(clientId, from, to));
    }

    @GetMapping("/topups")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopupHistoryResponse> getTopupHistory(
            @RequestParam(required = false) Long    clientId,
            @RequestParam(required = false) String  topupStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("Admin topup history query: clientId={}, topupStatus={}, from={}, to={}", clientId, topupStatus, from, to);
        return ResponseEntity.ok(spendService.getTopupHistory(clientId, topupStatus, from, to));
    }
}
