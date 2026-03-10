package com.softropic.sendam.client.repo;

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
}
