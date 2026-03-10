package com.softropic.sendam.client.api;

import com.softropic.sendam.client.contract.AdminClientDto;
import com.softropic.sendam.client.contract.CreateClientRequest;
import com.softropic.sendam.client.contract.CreateClientResponse;
import com.softropic.sendam.client.repo.ClientRepository;
import com.softropic.sendam.client.service.ClientService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin client management endpoints. Restricted to ROLE_ADMIN at the filter chain level
 * (AppEndpoints.ADMIN_CLIENTS).
 */
@RestController
@RequestMapping("/api/admin/clients")
public class AdminClientResource {

    private final ClientService clientService;
    private final ClientRepository clientRepository;

    public AdminClientResource(final ClientService clientService, final ClientRepository clientRepository) {
        this.clientService = clientService;
        this.clientRepository = clientRepository;
    }

    /**
     * Create a new client account.
     * POST /api/admin/clients
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateClientResponse createClient(@Valid @RequestBody final CreateClientRequest request) {
        return clientService.createClient(request);
    }

    /**
     * List all client accounts with their current available credit balance.
     * GET /api/admin/clients
     * No pagination — admin use only; client count is bounded for v1.
     */
    @GetMapping
    public ResponseEntity<List<AdminClientDto>> getAllClients() {
        return ResponseEntity.ok(clientRepository.findAllWithBalance());
    }
}
