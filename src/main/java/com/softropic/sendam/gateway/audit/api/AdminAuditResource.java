package com.softropic.sendam.gateway.audit.api;

import com.softropic.sendam.gateway.audit.contract.AuditEventRow;
import com.softropic.sendam.gateway.audit.service.AuditEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminAuditResource {

    private final AuditEventService auditEventService;

    /**
     * AUDT-05: Returns a paginated page of audit events.
     * All filter params are optional — omitting them returns all events.
     *
     * @param clientId filter by client (optional)
     * @param from     filter by occurred_at >= from (optional, ISO-8601 instant)
     * @param to       filter by occurred_at <= to (optional, ISO-8601 instant)
     * @param pageable Spring Data pageable (default: page=0, size=20, sort=occurred_at DESC)
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AuditEventRow>> getEvents(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "occurred_at", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(auditEventService.findEvents(clientId, from, to, pageable));
    }
}
