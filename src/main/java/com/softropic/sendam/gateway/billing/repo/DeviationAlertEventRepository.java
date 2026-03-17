package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviationAlertEventRepository extends JpaRepository<DeviationAlertEvent, Long> {
    List<DeviationAlertEvent> findBySegmentAlertIdFkOrderByActedAtAsc(Long segmentAlertIdFk);
    List<DeviationAlertEvent> findByBalanceAlertIdFkOrderByActedAtAsc(Long balanceAlertIdFk);
}
