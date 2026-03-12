package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderUnavailableException;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipient;

import java.util.List;

public interface SmsSender {
    /**
     * Submits a batch of recipients for a SendRequest to the external provider.
     *
     * @param request the parent SendRequest
     * @param recipients the list of recipients to submit
     * @throws ProviderUnavailableException if the provider is down
     */
    void send(SendRequest request, List<SendRequestRecipient> recipients) throws ProviderUnavailableException;
}
