package com.softropic.sendam.gateway.billing.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceDeviationAlertRepository extends JpaRepository<BalanceDeviationAlert, Long> {
    // Phase 24 will add query methods for listing/filtering deviation alerts
}
