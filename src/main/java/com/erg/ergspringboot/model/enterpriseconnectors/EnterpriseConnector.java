package com.erg.ergspringboot.model.enterpriseconnectors;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "enterprise_connectors", indexes = {
        @Index(name = "idx_enterprise_connectors_tenant_status", columnList = "tenant_id, status, updated_at"),
        @Index(name = "ux_enterprise_connectors_provider", columnList = "tenant_id, provider_key", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class EnterpriseConnector extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;
    @Column(name = "provider_kind", nullable = false, length = 16)
    private String providerKind;
    @Column(name = "adapter_key", nullable = false, length = 64)
    private String adapterKey;
    @Column(name = "provider_key", nullable = false, length = 96)
    private String providerKey;
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;
    @Column(name = "endpoint", length = 255)
    private String endpoint;
    @Column(name = "secret_ref", nullable = false, length = 255)
    private String secretRef;
    @Column(name = "mapping_version", nullable = false, length = 64)
    private String mappingVersion;
    @Column(name = "mapping_hash", nullable = false, length = 64)
    private String mappingHash;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "created_by", nullable = false, length = 96)
    private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 96)
    private String updatedBy;
    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
