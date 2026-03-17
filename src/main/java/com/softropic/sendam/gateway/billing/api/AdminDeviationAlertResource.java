package com.softropic.sendam.gateway.billing.api;

import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertDto;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertNoteRequest;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import com.softropic.sendam.gateway.billing.service.DeviationAlertManagementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin REST API for deviation alert lifecycle management.
 *
 * Restricted to ROLE_ADMIN at two layers:
 * - Filter chain: AppEndpoints.ADMIN_DEVIATION_ALERTS (/api/admin/deviations/**)
 * - Method security: class-level @PreAuthorize
 *
 * Four endpoints:
 *   GET  /api/admin/deviations/alerts                  — paginated list, optional type+status filter
 *   GET  /api/admin/deviations/{type}/{id}             — single alert with full audit trail
 *   PUT  /api/admin/deviations/{type}/{id}/acknowledge — transition OPEN→ACKNOWLEDGED
 *   PUT  /api/admin/deviations/{type}/{id}/resolve     — transition OPEN|ACKNOWLEDGED→RESOLVED
 *
 * {type} path variable is the DeviationAlertType enum value (SEGMENT, PLATFORM_FREEZE, BALANCE).
 * Including type in the path eliminates ambiguity about which underlying table to query.
 */
@RestController
@RequestMapping("/api/admin/deviations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminDeviationAlertResource {

    private final DeviationAlertManagementService deviationAlertManagementService;

    /**
     * List deviation alerts, optionally filtered by type and/or alertStatus.
     * When type is omitted, all three alert types are merged and sorted by createdDate DESC.
     * auditTrail is null on list items — use the detail endpoint for the full trail.
     *
     * GET /api/admin/deviations/alerts
     */
    @GetMapping("/alerts")
    public ResponseEntity<Page<DeviationAlertDto>> listAlerts(
            @RequestParam(required = false) DeviationAlertType type,
            @RequestParam(required = false) AlertStatus alertStatus,
            @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("Admin listing deviation alerts: type={}, alertStatus={}", type, alertStatus);
        return ResponseEntity.ok(deviationAlertManagementService.listAlerts(type, alertStatus, pageable));
    }

    /**
     * Fetch a single deviation alert by type and id, including full audit trail.
     *
     * GET /api/admin/deviations/{type}/{id}
     */
    @GetMapping("/{type}/{id}")
    public ResponseEntity<DeviationAlertDto> getAlert(
            @PathVariable DeviationAlertType type,
            @PathVariable Long id) {
        log.debug("Admin fetching deviation alert: type={}, id={}", type, id);
        return ResponseEntity.ok(deviationAlertManagementService.getAlert(type, id));
    }

    /**
     * Acknowledge a deviation alert — transitions OPEN→ACKNOWLEDGED.
     * Requires a mandatory free-text note (DEVMGMT-03).
     * Throws 409 on invalid transition (alert already ACKNOWLEDGED or RESOLVED).
     *
     * PUT /api/admin/deviations/{type}/{id}/acknowledge
     */
    @PutMapping("/{type}/{id}/acknowledge")
    public ResponseEntity<DeviationAlertDto> acknowledge(
            @PathVariable DeviationAlertType type,
            @PathVariable Long id,
            @Valid @RequestBody DeviationAlertNoteRequest request) {
        log.info("Admin acknowledging deviation alert: type={}, id={}", type, id);
        return ResponseEntity.ok(deviationAlertManagementService.acknowledge(type, id, request.note()));
    }

    /**
     * Resolve a deviation alert — transitions OPEN|ACKNOWLEDGED→RESOLVED.
     * Requires a mandatory free-text note (DEVMGMT-04).
     * Throws 409 on invalid transition (alert already RESOLVED).
     *
     * PUT /api/admin/deviations/{type}/{id}/resolve
     */
    @PutMapping("/{type}/{id}/resolve")
    public ResponseEntity<DeviationAlertDto> resolve(
            @PathVariable DeviationAlertType type,
            @PathVariable Long id,
            @Valid @RequestBody DeviationAlertNoteRequest request) {
        log.info("Admin resolving deviation alert: type={}, id={}", type, id);
        return ResponseEntity.ok(deviationAlertManagementService.resolve(type, id, request.note()));
    }
}
