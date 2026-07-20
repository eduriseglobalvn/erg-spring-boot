package com.erg.ergspringboot.model.quiz.version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Compiled + signed runtime package (spec §1.3). Id is the human-meaningful
 * {@code pkg_{quizId}_{versionLabel}_{hashPrefix}} so it does not extend BaseEntity. Immutable and
 * content-addressed — the long-lived cache target. {@code packageJson} never carries the answer key
 * for a server-authoritative (testing) grading mode.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "quiz_packages",
        indexes = {
                @Index(name = "idx_quiz_packages_quiz_version", columnList = "quiz_id, quiz_version"),
                @Index(name = "idx_quiz_packages_tenant_quiz", columnList = "tenant_id, quiz_id")
        }
)
public class QuizPackage {

    @Id
    @Column(name = "id", length = 128, nullable = false, updatable = false)
    private String id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "quiz_id", nullable = false)
    private String quizId;

    @Column(name = "quiz_version_id", nullable = false)
    private String quizVersionId;

    @Column(name = "quiz_version")
    private String quizVersion;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "content_hash")
    private String contentHash;

    private String signature;

    @Column(name = "public_key_id")
    private String publicKeyId;

    @Column(name = "grading_mode", nullable = false)
    private String gradingMode;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "package_format", nullable = false)
    private String packageFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assets_manifest", columnDefinition = "jsonb")
    private Map<String, Object> assetsManifest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "package_json", columnDefinition = "jsonb")
    private Map<String, Object> packageJson;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
