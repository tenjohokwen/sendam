package com.softropic.sendam.client.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    Page<CreditLedgerEntry> findByClientIdOrderByCreatedDateDesc(Long clientId, Pageable pageable);
}
