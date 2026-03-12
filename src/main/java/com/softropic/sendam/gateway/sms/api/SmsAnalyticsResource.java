package com.softropic.sendam.gateway.sms.api;

import com.softropic.sendam.gateway.sms.contract.ClientDeliveryStatsResponse;
import com.softropic.sendam.gateway.sms.contract.ClientSegmentTotalsResponse;
import com.softropic.sendam.gateway.sms.service.DeliveryAnalyticsService;
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
@RequestMapping("/v1/sms/analytics")
@RequiredArgsConstructor
@Slf4j
public class SmsAnalyticsResource {

    private final DeliveryAnalyticsService analyticsService;

    @GetMapping("/delivery-stats")
    public ResponseEntity<ClientDeliveryStatsResponse> getDeliveryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("Client delivery stats query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getClientDeliveryStats(clientId, from, to));
    }

    @GetMapping("/segment-totals")
    public ResponseEntity<ClientSegmentTotalsResponse> getSegmentTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("Client segment totals query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getClientSegmentTotals(clientId, from, to));
    }
}
