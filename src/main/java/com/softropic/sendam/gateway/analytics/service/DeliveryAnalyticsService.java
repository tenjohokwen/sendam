package com.softropic.sendam.gateway.analytics.service;

import com.softropic.sendam.gateway.analytics.contract.ClientDeliveryStatsResponse;
import com.softropic.sendam.gateway.analytics.contract.ClientSegmentTotalsResponse;
import com.softropic.sendam.gateway.analytics.contract.DeliveryDailyStat;
import com.softropic.sendam.gateway.analytics.contract.DeliveryDailyStatRow;
import com.softropic.sendam.gateway.analytics.contract.DeliveryStatRow;
import com.softropic.sendam.gateway.analytics.contract.DeliveryStatsResponse;
import com.softropic.sendam.gateway.analytics.contract.SegmentTotalsResponse;
import com.softropic.sendam.gateway.analytics.repo.DeliveryAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryAnalyticsService {

    private final DeliveryAnalyticsRepository repository;

    public DeliveryStatsResponse getDeliveryStats(Long clientId, Instant from, Instant to) {
        DeliveryStatRow summary = repository.findDeliveryStats(clientId, from, to);
        List<DeliveryDailyStatRow> dailyRows = repository.findDailyBreakdown(clientId, from, to);

        long totalSent = summary.getTotalSent();
        long delivered = summary.getDelivered();
        long failed    = summary.getFailed();
        double rate    = totalSent == 0 ? 0.0 : (double) delivered / totalSent * 100.0;

        List<DeliveryDailyStat> breakdown = dailyRows.stream()
            .map(r -> new DeliveryDailyStat(
                r.getDay().toLocalDate().toString(),
                r.getTotalSent(),
                r.getDelivered(),
                r.getFailed()
            ))
            .toList();

        return new DeliveryStatsResponse(totalSent, delivered, failed, rate, summary.getTotalSegments(), breakdown);
    }

    public SegmentTotalsResponse getSegmentTotals(Long clientId, Instant from, Instant to) {
        DeliveryStatRow summary = repository.findDeliveryStats(clientId, from, to);
        return new SegmentTotalsResponse(summary.getTotalSegments(), clientId, from, to);
    }

    public ClientDeliveryStatsResponse getClientDeliveryStats(Long clientId, Instant from, Instant to) {
        DeliveryStatRow summary = repository.findDeliveryStats(clientId, from, to);
        long totalSent  = summary.getTotalSent();
        long delivered  = summary.getDelivered();
        long failed     = summary.getFailed();
        double rate     = totalSent == 0 ? 0.0 : (double) delivered / totalSent * 100.0;
        return new ClientDeliveryStatsResponse(totalSent, delivered, failed, rate, summary.getTotalSegments());
    }

    public ClientSegmentTotalsResponse getClientSegmentTotals(Long clientId, Instant from, Instant to) {
        DeliveryStatRow summary = repository.findDeliveryStats(clientId, from, to);
        return new ClientSegmentTotalsResponse(summary.getTotalSegments(), from, to);
    }
}
