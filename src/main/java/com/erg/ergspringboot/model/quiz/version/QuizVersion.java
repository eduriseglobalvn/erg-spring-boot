package com.erg.ergspringboot.model.quiz.version;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable publish snapshot (spec §1.3). Freezes the authoring document and the compiled runtime quiz
 * plus its secret answer key. INSERT-only; the only mutation ever applied is flipping {@code isActive}
 * when a newer version is published. {@code answerKey} is secret and must never enter a testing package.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "quiz_versions",
        indexes = {
                @Index(name = "idx_quiz_versions_tenant_quiz", columnList = "tenant_id, quiz_id, version_number")
        }
)
public class QuizVersion extends BaseEntity {

    @Column(name = "quiz_id", nullable = false)
    private String quizId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "version_label", nullable = false)
    private String versionLabel;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authoring_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> authoringSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_quiz", columnDefinition = "jsonb")
    private Map<String, Object> runtimeQuiz;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_key", columnDefinition = "jsonb")
    private Map<String, Object> answerKey;

    @Column(name = "grading_mode", nullable = false)
    private String gradingMode;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "idempotency_key")
    private String idempotencyKey;
}
