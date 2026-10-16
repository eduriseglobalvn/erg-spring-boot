package com.erg.ergspringboot.model.learningstandards;

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
@Table(name = "learning_standard_packages", indexes = {
        @Index(name = "idx_learning_standard_packages_tenant_status", columnList = "tenant_id, status, updated_at"),
        @Index(name = "idx_learning_standard_packages_tenant_artifact", columnList = "tenant_id, artifact_key", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LearningPackage extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;
    @Column(name = "title", nullable = false, length = 255)
    private String title;
    @Column(name = "standard", nullable = false, length = 32)
    private String standard;
    @Column(name = "package_version", nullable = false, length = 96)
    private String packageVersion;
    @Column(name = "artifact_key", nullable = false, length = 255)
    private String artifactKey;
    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;
    @Column(name = "byte_size", nullable = false)
    private Long byteSize;
    @Column(name = "manifest_sha256", nullable = false, length = 64)
    private String manifestSha256;
    @Column(name = "manifest_identifier", length = 255)
    private String manifestIdentifier;
    @Column(name = "launch_path", nullable = false, length = 255)
    private String launchPath;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "quarantine_reason", length = 255)
    private String quarantineReason;
    @Column(name = "created_by", nullable = false, length = 96)
    private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 96)
    private String updatedBy;
    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
