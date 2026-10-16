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
@Table(name = "learning_standard_lti_registrations", indexes = {
        @Index(name = "idx_lti_registrations_tenant_issuer", columnList = "tenant_id, issuer, client_id", unique = true),
        @Index(name = "idx_lti_registrations_tenant_status", columnList = "tenant_id, status, updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LtiRegistration extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;
    @Column(name = "issuer", nullable = false, length = 255)
    private String issuer;
    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;
    @Column(name = "deployment_id", nullable = false, length = 255)
    private String deploymentId;
    @Column(name = "public_key_pem", nullable = false, columnDefinition = "text")
    private String publicKeyPem;
    @Column(name = "allowed_target_host", nullable = false, length = 255)
    private String allowedTargetHost;
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
