package com.softropic.sendam.gateway.spend.service;

import com.softropic.sendam.gateway.analytics.contract.ClientCreditConsumptionResponse;
import com.softropic.sendam.gateway.spend.contract.SpendSummaryResponse;
import com.softropic.sendam.gateway.spend.contract.SpendSummaryRow;
import com.softropic.sendam.gateway.spend.contract.TopupHistoryItem;
import com.softropic.sendam.gateway.spend.contract.TopupHistoryResponse;
import com.softropic.sendam.gateway.spend.contract.TopupHistoryRow;
import com.softropic.sendam.gateway.spend.repo.SpendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendService {

    private final SpendRepository repository;

    public SpendSummaryResponse getSpendSummary(Long clientId, Instant from, Instant to) {
        SpendSummaryRow row = repository.findSpendSummary(clientId, from, to);
        return new SpendSummaryResponse(
            row.getSmsDebit(),
            row.getSmsRefund(),
            row.getTopupApproved(),
            row.getSmsReservation(),
            row.getNetCreditsConsumed()
        );
    }

    public ClientCreditConsumptionResponse getClientNetCreditsConsumed(Long clientId, Instant from, Instant to) {
        SpendSummaryRow row = repository.findSpendSummary(clientId, from, to);
        return new ClientCreditConsumptionResponse(row.getNetCreditsConsumed(), from, to);
    }

    public TopupHistoryResponse getTopupHistory(Long clientId, String topupStatus, Instant from, Instant to) {
        List<TopupHistoryRow> rows = repository.findTopupHistory(clientId, topupStatus, from, to);
        List<TopupHistoryItem> items = rows.stream()
            .map(r -> new TopupHistoryItem(
                r.getId(),
                r.getClientId(),
                r.getAmount(),
                r.getTransactionId(),
                r.getPaymentType(),
                r.getAccountNumber(),
                r.getTopupStatus(),
                r.getCreatedDate().toInstant(),     // java.sql.Timestamp -> Instant
                r.getApprovedAt()  != null ? r.getApprovedAt().toInstant()  : null,
                r.getRejectedAt()  != null ? r.getRejectedAt().toInstant()  : null
            ))
            .toList();
        return new TopupHistoryResponse(items);
    }
}
