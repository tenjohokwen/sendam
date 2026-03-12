package com.softropic.sendam.gateway.sms.api;

import com.softropic.sendam.gateway.sms.contract.DlrRow;
import com.softropic.sendam.gateway.sms.contract.ScheduledSmsRow;
import com.softropic.sendam.gateway.sms.service.AdminSmsMonitorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sms/monitor")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminSmsMonitorResource {

    private final AdminSmsMonitorService adminSmsMonitorService;

    /**
     * SMSM-01: Returns paginated scheduled SMS requests (ACCEPTED + scheduleTime IS NOT NULL).
     * Optional filter: clientId. Sort: scheduleTime ASC.
     */
    @GetMapping("/scheduled")
    public ResponseEntity<Page<ScheduledSmsRow>> getScheduledSms(
            @RequestParam(required = false) Long clientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminSmsMonitorService.getScheduledSms(pageable));
    }

    /**
     * SMSM-02: Returns per-recipient DLR rows for a given sendRequestId (String identifier).
     * No clientId binding — admin access sees all clients' DLRs.
     */
    @GetMapping("/scheduled/{sendRequestId}/dlr")
    public ResponseEntity<Page<DlrRow>> getDlr(
            @PathVariable String sendRequestId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminSmsMonitorService.getDlrForRequest(sendRequestId, pageable));
    }
}
