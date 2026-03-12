package com.softropic.sendam.gateway.sms.api;

import com.softropic.sendam.gateway.sms.contract.DeliveryStatsResponse;
import com.softropic.sendam.gateway.sms.contract.SegmentTotalsResponse;
import com.softropic.sendam.gateway.sms.service.DeliveryAnalyticsService;
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
 * Admin delivery analytics endpoints.
 */
@RestController
@RequestMapping("/api/admin/sms/analytics")
@RequiredArgsConstructor
@Slf4j
public class AdminSmsAnalyticsResource {

    private final DeliveryAnalyticsService analyticsService;

    /**
     * Delivery statistics with optional filtering.
     * GET /api/admin/sms/analytics/delivery-stats
     */
    @GetMapping("/delivery-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryStatsResponse> getDeliveryStats(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("Admin delivery stats query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getDeliveryStats(clientId, from, to));
    }

    /**
     * Segment totals with optional filtering.
     * GET /api/admin/sms/analytics/segment-totals
     */
    @GetMapping("/segment-totals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SegmentTotalsResponse> getSegmentTotals(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("Admin segment totals query: clientId={}, from={}, to={}", clientId, from, to);
        return ResponseEntity.ok(analyticsService.getSegmentTotals(clientId, from, to));
    }
}
