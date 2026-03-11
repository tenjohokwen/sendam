package com.softropic.sendam.gateway.account.repo;

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
@Table(name = "client_account", schema = "main")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity extends AbstractAuditingEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Builder.Default
    protected EntityStatus status = EntityStatus.ACTIVE;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
