package com.softropic.sendam.gateway.billing.repo;

import com.softropic.sendam.gateway.billing.contract.PlatformLedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformCreditLedgerRepository extends JpaRepository<PlatformCreditLedgerEntry, Long> {

    @Query("SELECT e FROM PlatformCreditLedgerEntry e WHERE (:type IS NULL OR e.entryType = :type) ORDER BY e.createdDate DESC")
    Page<PlatformCreditLedgerEntry> findByOptionalType(@Param("type") PlatformLedgerEntryType type, Pageable pageable);
}
