package com.softropic.sendam.gateway.analytics.api;

import com.softropic.sendam.gateway.analytics.contract.ClientCreditConsumptionResponse;
import com.softropic.sendam.gateway.analytics.contract.ClientDeliveryStatsResponse;
import com.softropic.sendam.gateway.analytics.contract.ClientSegmentTotalsResponse;
import com.softropic.sendam.gateway.analytics.service.DeliveryAnalyticsService;
import com.softropic.sendam.gateway.billing.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class ClientAnalyticsResource {

    private final DeliveryAnalyticsService analyticsService;
    private final CreditService creditService;

    @GetMapping("/delivery-stats")
    public ResponseEntity<ClientDeliveryStatsResponse> getDeliveryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("Client delivery stats: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getClientDeliveryStats(clientId, from, to));
    }

    @GetMapping("/segment-totals")
    public ResponseEntity<ClientSegmentTotalsResponse> getSegmentTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("Client segment totals: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getClientSegmentTotals(clientId, from, to));
    }

    @GetMapping("/credits-consumed")
    public ResponseEntity<ClientCreditConsumptionResponse> getCreditConsumption(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("Client credits consumed: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(creditService.getNetCreditsConsumed(clientId, from, to));
    }
}
