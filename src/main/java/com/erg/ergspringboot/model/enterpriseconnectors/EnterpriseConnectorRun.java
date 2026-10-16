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
@Table(name = "enterprise_connector_runs", indexes = {
        @Index(name = "idx_enterprise_connector_runs_tenant_connector", columnList = "tenant_id, connector_id, created_at"),
        @Index(name = "idx_enterprise_connector_runs_tenant_status", columnList = "tenant_id, status, updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class EnterpriseConnectorRun extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;
    @Column(name = "connector_id", nullable = false, length = 36)
    private String connectorId;
    @Column(name = "mode", nullable = false, length = 32)
    private String mode;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "processed", nullable = false)
    private Integer processed = 0;
    @Column(name = "created_count", nullable = false)
    private Integer createdCount = 0;
    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount = 0;
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    @Column(name = "sample_external_ids", length = 2048)
    private String sampleExternalIds;
    @Column(name = "error_code", length = 96)
    private String errorCode;
    @Column(name = "requested_by", nullable = false, length = 96)
    private String requestedBy;
    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
