package com.softropic.sendam.client.api;

import com.softropic.sendam.client.contract.CreateClientRequest;
import com.softropic.sendam.client.contract.CreateClientResponse;
import com.softropic.sendam.client.service.ClientService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/clients")
public class AdminClientResource {

    private final ClientService clientService;

    public AdminClientResource(final ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateClientResponse createClient(@Valid @RequestBody final CreateClientRequest request) {
        return clientService.createClient(request);
    }
}
