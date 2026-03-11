package com.softropic.sendam.gateway.analytics.api;

import com.softropic.sendam.gateway.analytics.contract.DeliveryStatsResponse;
import com.softropic.sendam.gateway.analytics.contract.SegmentTotalsResponse;
import com.softropic.sendam.gateway.analytics.service.DeliveryAnalyticsService;
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
 * Restricted to ROLE_ADMIN both at the filter chain level (AppEndpoints.ADMIN_ANALYTICS) and
 * via @PreAuthorize for belt-and-suspenders defence.
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Slf4j
public class AdminDeliveryAnalyticsResource {

    private final DeliveryAnalyticsService analyticsService;

    /**
     * Delivery statistics with optional filtering.
     * GET /api/admin/analytics/delivery-stats
     * Returns total_sent, delivered, failed, delivery_rate, total_segments, daily_breakdown.
     * All filters are optional — omitting any returns unfiltered results.
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
     * GET /api/admin/analytics/segment-totals
     * Returns total_segments with the applied filter echoed back.
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
