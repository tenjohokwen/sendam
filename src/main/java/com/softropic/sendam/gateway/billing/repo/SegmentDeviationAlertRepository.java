package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.AlertStatus;
import com.softropic.sendam.gateway.billing.contract.DeviationAlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegmentDeviationAlertRepository extends JpaRepository<SegmentDeviationAlert, Long> {

    /**
     * Paginated listing with optional type and alertStatus filters.
     * Both params use the nullable-enum JPQL pattern: (:param IS NULL OR e.field = :param).
     * Use JPQL (not nativeQuery=true) — JPQL handles null enum params without CAST workarounds.
     * type filter accepts SEGMENT or PLATFORM_FREEZE (both live on this table).
     */
    @Query("""
            SELECT a FROM SegmentDeviationAlert a
            WHERE (:type IS NULL OR a.alertType = :type)
              AND (:alertStatus IS NULL OR a.alertStatus = :alertStatus)
            ORDER BY a.createdDate DESC
            """)
    Page<SegmentDeviationAlert> findByOptionalFilters(
            @Param("type") DeviationAlertType type,
            @Param("alertStatus") AlertStatus alertStatus,
            Pageable pageable);
}
