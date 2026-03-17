package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentDeviationAlertRepository extends JpaRepository<SegmentDeviationAlert, Long> {
    // Phase 24 will add query methods for listing/filtering
}
