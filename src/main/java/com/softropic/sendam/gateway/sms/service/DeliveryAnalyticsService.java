package com.softropic.sendam.gateway.sms.service;

import com.softropic.sendam.gateway.sms.contract.ClientDeliveryStatsResponse;
import com.softropic.sendam.gateway.sms.contract.ClientSegmentTotalsResponse;
import com.softropic.sendam.gateway.sms.contract.DeliveryDailyStat;
import com.softropic.sendam.gateway.sms.contract.DeliveryDailyStatRow;
import com.softropic.sendam.gateway.sms.contract.DeliveryStatRow;
import com.softropic.sendam.gateway.sms.contract.DeliveryStatsResponse;
import com.softropic.sendam.gateway.sms.contract.SegmentTotalsResponse;
import com.softropic.sendam.gateway.sms.repo.DeliveryAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DeliveryAnalyticsService {

    private final DeliveryAnalyticsRepository repository;

    public DeliveryStatsResponse getDeliveryStats(Long clientId, Instant from, Instant to) {
        DeliveryStatRow totals = repository.findDeliveryStats(clientId, from, to);
        List<DeliveryDailyStatRow> dailyRows = repository.findDailyBreakdown(clientId, from, to);

        List<DeliveryDailyStat> dailyStats = dailyRows.stream()
                .map(r -> new DeliveryDailyStat(
                        r.getDay().toString(),
                        r.getTotalSent(),
                        r.getDelivered(),
                        r.getFailed()
                ))
                .toList();

        return new DeliveryStatsResponse(
                totals.getTotalSent(),
                totals.getDelivered(),
                totals.getFailed(),
                calculateRate(totals.getDelivered(), totals.getTotalSent()),
                totals.getTotalSegments(),
                dailyStats
        );
    }

    public SegmentTotalsResponse getSegmentTotals(Long clientId, Instant from, Instant to) {
        DeliveryStatRow row = repository.findDeliveryStats(clientId, from, to);
        return new SegmentTotalsResponse(row.getTotalSegments(), clientId, from, to);
    }

    public ClientDeliveryStatsResponse getClientDeliveryStats(Long clientId, Instant from, Instant to) {
        DeliveryStatRow row = repository.findDeliveryStats(clientId, from, to);
        return new ClientDeliveryStatsResponse(
                row.getTotalSent(),
                row.getDelivered(),
                row.getFailed(),
                calculateRate(row.getDelivered(), row.getTotalSent()),
                row.getTotalSegments()
        );
    }

    public ClientSegmentTotalsResponse getClientSegmentTotals(Long clientId, Instant from, Instant to) {
        DeliveryStatRow row = repository.findDeliveryStats(clientId, from, to);
        return new ClientSegmentTotalsResponse(row.getTotalSegments(), from, to);
    }

    private double calculateRate(long delivered, long total) {
        if (total == 0) return 0.0;
        return (double) delivered / total;
    }
}
