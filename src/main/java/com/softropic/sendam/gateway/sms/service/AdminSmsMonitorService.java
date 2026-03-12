package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.DlrRow;
import com.softropic.sendam.gateway.sms.contract.ScheduledSmsRow;
import com.softropic.sendam.gateway.sms.repo.SendRequest;
import com.softropic.sendam.gateway.sms.repo.SendRequestRecipientRepository;
import com.softropic.sendam.gateway.sms.repo.SendRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSmsMonitorService {

    private final SendRequestRepository sendRequestRepository;
    private final SendRequestRecipientRepository recipientRepository;

    public Page<ScheduledSmsRow> getScheduledSms(Pageable pageable) {
        return sendRequestRepository.findScheduledAccepted(pageable)
            .map(s -> new ScheduledSmsRow(
                s.getId(),
                s.getClientId(),
                s.getSendRequestId(),
                s.getSender(),
                s.getMessageCount(),
                s.getScheduleTime(),
                s.getReservedCredits()
            ));
    }

    public Page<DlrRow> getDlrForRequest(String sendRequestId, Pageable pageable) {
        SendRequest req = sendRequestRepository.findBySendRequestId(sendRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "sendRequestId not found: " + sendRequestId));
        return recipientRepository.findBySendRequestIdFk(req.getId(), pageable)
            .map(r -> new DlrRow(
                r.getId(),
                r.getRecipient(),
                r.getSendStatus().name(),
                r.getGatewayMessageId(),
                r.getProviderMessageId(),
                r.getSegmentsConsumed()
            ));
    }
}
