package com.erg.ergspringboot.model.quiz.runtime;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Live per-student progress projection for the teacher dashboard (spec §1.7). Recomputed on each
 * attempt sync/submit. This is a derived read model, not a source of truth — attempts/events are.
 *
 * <p>Note: extends {@link BaseEntity} for the id/createdAt/updatedAt convention; the migration table
 * carries the same columns plus the scope/quiz/progress fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "student_progress",
        indexes = {
                @Index(name = "idx_student_progress_quiz_status", columnList = "quiz_id, status"),
                @Index(name = "idx_student_progress_class_quiz", columnList = "class_id, quiz_id")
        }
)
public class StudentProgress extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "student_user_id", nullable = false)
    private String studentUserId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "quiz_id", nullable = false)
    private String quizId;

    @Column(name = "quiz_title")
    private String quizTitle;

    @Column(name = "assignment_id")
    private String assignmentId;

    @Column(name = "class_id")
    private String classId;

    private String mode;

    private String status;

    @Column(name = "current_question_index")
    private Integer currentQuestionIndex;

    @Column(name = "current_question_title")
    private String currentQuestionTitle;

    @Column(name = "answered_count", nullable = false)
    private Integer answeredCount;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "percent_complete")
    private BigDecimal percentComplete;

    private BigDecimal score;

    private Boolean passed;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "last_active_at")
    private OffsetDateTime lastActiveAt;
}
