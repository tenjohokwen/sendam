package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BalanceDeviationAlertRepository extends JpaRepository<BalanceDeviationAlert, Long> {

    /**
     * Paginated listing with optional alertStatus filter.
     * type param is accepted for API symmetry but BALANCE alerts always have type=BALANCE;
     * including it keeps the call-site pattern uniform across both repos.
     */
    @Query("""
            SELECT a FROM BalanceDeviationAlert a
            WHERE (:alertStatus IS NULL OR a.alertStatus = :alertStatus)
            ORDER BY a.createdDate DESC
            """)
    Page<BalanceDeviationAlert> findByOptionalFilters(
            @Param("alertStatus") AlertStatus alertStatus,
            Pageable pageable);
}
