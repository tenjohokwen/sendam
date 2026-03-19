package com.softropic.sendam.gateway.billing.service;

import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
import com.softropic.sendam.gateway.billing.repo.PlatformFreezeState;
import com.softropic.sendam.gateway.billing.repo.PlatformFreezeStateRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;
import com.softropic.sendam.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Business logic for platform-level freeze lifecycle.
 *
 * <p>Lives in gateway.billing.service to avoid cross-module service dependencies:
 * CreditReservationService (also in billing) calls isFrozen() within the same package,
 * keeping the billing module self-contained for credit guard-rail checks.
 *
 * <p>Injects SendRequestRepository (gateway.sms.repo) directly to bulk-update SMS state
 * atomically within the same @Transactional context as the platform freeze state mutation.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PlatformFreezeService {

    private final PlatformFreezeStateRepository freezeStateRepository;
    private final SendRequestRepository sendRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Freezes the platform: sets frozen=true, records reason + timestamp + optional shortfall,
     * bulk-suspends all ACCEPTED scheduled sends across all clients, publishes PLATFORM_FROZEN.
     *
     * @param reason          mandatory freeze reason (PFLAT-03)
     * @param shortfallAmount nullable — unrecovered shortfall amount if triggered by BOOK-06;
     *                        null for admin-initiated freeze
     */
    public void freeze(String reason, Long shortfallAmount) {
        PlatformFreezeState state = freezeStateRepository.findForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform freeze state not initialized", "platform_freeze_state"));

        state.setFrozen(true);
        state.setFrozenAt(Instant.now());
        state.setFreezeReason(reason);
        state.setShortfallAmount(shortfallAmount);
        state.setFreezeResolvedAt(null);
        state.setFreezeResolution(null);
        freezeStateRepository.save(state);

        int suspended = sendRequestRepository.suspendAllScheduled();
        log.warn("Platform frozen: reason='{}', shortfall={}, suspendedSms={}", reason, shortfallAmount, suspended);

        eventPublisher.publishEvent(new DomainAuditEvent(
                AuditEventType.PLATFORM_FROZEN,
                null,
                resolveActor(),
                "Platform frozen: reason=" + reason + ", shortfall=" + shortfallAmount + ", suspendedSms=" + suspended
        ));
    }

    /**
     * Lifts the platform freeze: sets frozen=false, records resolution note + timestamp,
     * resumes all SUSPENDED sends across all clients, publishes PLATFORM_UNFROZEN.
     *
     * @param resolution mandatory resolution note (PFLAT-05)
     */
    public void unfreeze(String resolution) {
        PlatformFreezeState state = freezeStateRepository.findForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform freeze state not initialized", "platform_freeze_state"));

        state.setFrozen(false);
        state.setFreezeResolvedAt(Instant.now());
        state.setFreezeResolution(resolution);
        freezeStateRepository.save(state);

        int resumed = sendRequestRepository.resumeAllScheduled();
        log.info("Platform unfrozen: resolution='{}', resumedSms={}", resolution, resumed);

        eventPublisher.publishEvent(new DomainAuditEvent(
                AuditEventType.PLATFORM_UNFROZEN,
                null,
                resolveActor(),
                "Platform unfrozen: resolution=" + resolution + ", resumedSms=" + resumed
        ));
    }

    /**
     * Non-locking read of the platform frozen state.
     * Called by CreditReservationService (Plan 03) before accepting any credit reservation.
     *
     * @return true if the platform is frozen
     */
    @Transactional(readOnly = true)
    public boolean isFrozen() {
        return freezeStateRepository.findState()
                .map(PlatformFreezeState::isFrozen)
                .orElse(false);
    }

    private String resolveActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getName() != null ? auth.getName() : "system";
        } catch (Exception e) {
            return "system";
        }
    }
}
