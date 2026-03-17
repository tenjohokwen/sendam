package com.softropic.sendam.gateway.account.service;

import com.softropic.sendam.gateway.account.repo.ClientEntity;
import com.softropic.sendam.gateway.account.repo.ClientRepository;
import com.softropic.sendam.gateway.audit.contract.AuditEventType;
import com.softropic.sendam.gateway.audit.contract.DomainAuditEvent;
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
 * Business logic for client-level freeze lifecycle.
 *
 * <p>Intentional cross-module dependency: this service in gateway.account.service
 * injects SendRequestRepository from gateway.sms.repo. This is an accepted architectural
 * decision: the freeze service is a domain-level orchestrator that needs to bulk-update
 * SMS state atomically within the same @Transactional context as the freeze state mutation.
 * The alternative (a thin SmsSchedulerService) would add a layer without meaningful isolation
 * since the operation semantics are fundamentally coupled.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ClientFreezeService {

    private final ClientRepository clientRepository;
    private final SendRequestRepository sendRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Freezes a client account: sets frozen=true, records reason + timestamp,
     * bulk-suspends all ACCEPTED scheduled sends, and publishes CLIENT_ACCOUNT_FROZEN.
     * All writes occur in the caller's transaction (propagation=REQUIRED).
     *
     * @param clientId client to freeze
     * @param reason   mandatory freeze reason (CFREEZE-03)
     * @throws ResourceNotFoundException if the client does not exist
     */
    public void freeze(Long clientId, String reason) {
        ClientEntity client = clientRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + clientId, "client_account"));

        client.setFrozen(true);
        client.setFrozenAt(Instant.now());
        client.setFreezeReason(reason);
        client.setFreezeResolvedAt(null);
        client.setFreezeResolution(null);
        clientRepository.save(client);

        int suspended = sendRequestRepository.suspendScheduledForClient(clientId);
        log.info("Client {} frozen: reason='{}', suspendedSms={}", clientId, reason, suspended);

        eventPublisher.publishEvent(new DomainAuditEvent(
                AuditEventType.CLIENT_ACCOUNT_FROZEN,
                clientId,
                resolveActor(),
                "Client frozen: reason=" + reason + ", suspendedSms=" + suspended
        ));
    }

    /**
     * Unfreezes a client account: sets frozen=false, records resolution note + timestamp,
     * resumes all SUSPENDED sends, and publishes CLIENT_ACCOUNT_UNFROZEN.
     * All writes occur in the caller's transaction.
     *
     * @param clientId   client to unfreeze
     * @param resolution mandatory resolution note (CFREEZE-04)
     * @throws ResourceNotFoundException if the client does not exist
     */
    public void unfreeze(Long clientId, String resolution) {
        ClientEntity client = clientRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + clientId, "client_account"));

        client.setFrozen(false);
        client.setFreezeResolvedAt(Instant.now());
        client.setFreezeResolution(resolution);
        clientRepository.save(client);

        int resumed = sendRequestRepository.resumeScheduledForClient(clientId);
        log.info("Client {} unfrozen: resolution='{}', resumedSms={}", clientId, resolution, resumed);

        eventPublisher.publishEvent(new DomainAuditEvent(
                AuditEventType.CLIENT_ACCOUNT_UNFROZEN,
                clientId,
                resolveActor(),
                "Client unfrozen: resolution=" + resolution + ", resumedSms=" + resumed
        ));
    }

    /**
     * Non-locking read of the client's frozen state.
     * Called by CreditReservationService (Plan 03) before accepting a credit reservation.
     *
     * @param clientId client to check
     * @return true if the account is frozen
     * @throws ResourceNotFoundException if the client does not exist
     */
    @Transactional(readOnly = true)
    public boolean isFrozen(Long clientId) {
        ClientEntity client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + clientId, "client_account"));
        return client.isFrozen();
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
