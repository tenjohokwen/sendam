package com.softropic.sendam.gateway.sms.repo;

import com.softropic.sendam.gateway.provider.nexah.contract.ProviderStatsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SendRequestRecipientRepository extends JpaRepository<SendRequestRecipient, Long> {

    List<SendRequestRecipient> findBySendRequestIdFk(Long sendRequestIdFk);

    List<SendRequestRecipient> findBySendRequestIdFkAndClientId(Long sendRequestIdFk, Long clientId);

    Optional<SendRequestRecipient> findByGatewayMessageId(String gatewayMessageId);

    @Modifying
    @Query("DELETE FROM SendRequestRecipient r WHERE r.sendRequestIdFk IN :sendRequestIds")
    void deleteBySendRequestIdFkIn(@Param("sendRequestIds") List<Long> sendRequestIds);

    @Query(value = """
        SELECT
            COUNT(CASE WHEN r.send_status NOT IN ('ACCEPTED', 'CANCELLED') THEN 1 END) AS total_submitted,
            COUNT(CASE WHEN r.send_status IN ('COMPLETED', 'FAILED')        THEN 1 END) AS dr_received,
            COUNT(CASE WHEN r.send_status = 'FAILED'                        THEN 1 END) AS failed_count
        FROM main.send_request_recipient r
        """, nativeQuery = true)
    ProviderStatsRow findProviderStats();
}
