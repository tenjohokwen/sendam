package com.softropic.sendam.gateway.auth.repo;

import com.softropic.sendam.common.persistence.AbstractAuditingEntity;
import com.softropic.sendam.common.persistence.EntityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "client_api_key", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClientApiKeyEntity extends AbstractAuditingEntity {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "key_prefix", length = 24, unique = true, nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", length = 64, nullable = false)
    private String keyHash;

    @Column(name = "label", length = 100)
    private String label;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(final Long clientId) {
        this.clientId = clientId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(final String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(final String keyHash) {
        this.keyHash = keyHash;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }
}
