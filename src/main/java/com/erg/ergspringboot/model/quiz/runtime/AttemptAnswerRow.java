package com.erg.ergspringboot.model.quiz.runtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Normalized per-question grading result (spec §1.7) — the report source of truth. One row per
 * (attempt, published question), upserted only when {@code gradedAt} is newer so replays never
 * double-count. Essay rows carry {@code needsManualGrade=true} until a teacher grades them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(AttemptAnswerId.class)
@Table(
        name = "attempt_answers",
        indexes = {
                @Index(name = "idx_attempt_answers_question", columnList = "quiz_version_question_id"),
                @Index(name = "idx_attempt_answers_attempt", columnList = "attempt_id")
        }
)
public class AttemptAnswerRow {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private String attemptId;

    @Id
    @Column(name = "quiz_version_question_id", nullable = false)
    private String quizVersionQuestionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "question_kind")
    private String questionKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_answer", columnDefinition = "jsonb")
    private Object rawAnswer;

    private Boolean correct;

    @Column(name = "partially_right", nullable = false)
    private Boolean partiallyRight;

    @Column(name = "awarded_points", nullable = false)
    private BigDecimal awardedPoints;

    @Column(name = "max_points", nullable = false)
    private BigDecimal maxPoints;

    @Column(name = "graded_by")
    private String gradedBy;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    @Column(name = "needs_manual_grade", nullable = false)
    private Boolean needsManualGrade;
}
