package com.softropic.sendam.client.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientApiKeyRepository extends JpaRepository<ClientApiKeyEntity, Long> {
    Optional<ClientApiKeyEntity> findByKeyPrefix(String keyPrefix);
    List<ClientApiKeyEntity> findAllByClientId(Long clientId);
}
