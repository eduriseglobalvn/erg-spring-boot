package com.erg.ergspringboot.model.learningoutcomes;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outcome_certificates", indexes = {
        @Index(name = "idx_outcome_certificates_tenant_learner", columnList = "tenant_id, learner_id, issued_at"),
        @Index(name = "idx_outcome_certificates_tenant_path", columnList = "tenant_id, learning_path_id, status"),
        @Index(name = "ux_outcome_certificates_verification", columnList = "tenant_id, verification_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OutcomeCertificate extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "learner_id", nullable = false, length = 96)
    private String learnerId;

    @Column(name = "learning_path_id", nullable = false, length = 96)
    private String learningPathId;

    @Column(name = "certificate_number", nullable = false, length = 128)
    private String certificateNumber;

    @Column(name = "verification_id", nullable = false, length = 96)
    private String verificationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "evidence_ref", nullable = false, length = 255)
    private String evidenceRef;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "issued_by", nullable = false, length = 96)
    private String issuedBy;

    @Column(name = "revoked_by", length = 96)
    private String revokedBy;

    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
