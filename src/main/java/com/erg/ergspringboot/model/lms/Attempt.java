package com.erg.ergspringboot.model.lms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "lms_attempts",
        indexes = {
                @Index(name = "idx_lms_attempts_tenant_student", columnList = "tenant_id, student_id"),
                @Index(name = "idx_lms_attempts_tenant_assignment", columnList = "tenant_id, assignment_id"),
                @Index(name = "idx_lms_attempts_tenant_quiz", columnList = "tenant_id, quiz_id"),
                @Index(name = "idx_lms_attempts_status", columnList = "tenant_id, status")
        }
)
public class Attempt {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "assignment_id")
    private String assignmentId;

    @Column(name = "quiz_id")
    private String quizId;

    @Column(name = "student_id")
    private String studentId;

    @Column(name = "package_id")
    private String packageId;

    @Column(name = "package_hash")
    private String packageHash;

    @Column(name = "quiz_version")
    private String quizVersion;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "jsonb")
    private Map<String, AttemptAnswer> answers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events", columnDefinition = "jsonb")
    private List<Map<String, Object>> events;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client", columnDefinition = "jsonb")
    private Map<String, Object> client;

    private Double score;

    @Column(name = "max_score")
    private Double maxScore;

    private Double percent;

    private Boolean passed;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
