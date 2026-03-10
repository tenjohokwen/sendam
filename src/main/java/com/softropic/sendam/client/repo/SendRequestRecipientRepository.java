package com.softropic.sendam.client.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SendRequestRecipientRepository extends JpaRepository<SendRequestRecipient, Long> {

    List<SendRequestRecipient> findBySendRequestIdFk(Long sendRequestIdFk);

    List<SendRequestRecipient> findBySendRequestIdFkAndClientId(Long sendRequestIdFk, Long clientId);
}
