package com.erg.ergspringboot.model.quiz.version;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Normalized published question (spec §1.4). {@code body} is the runtime Question with the answer key
 * stripped; {@code answerKey} is the secret correct answer. {@code kind}/{@code points}/{@code gradingPolicy}
 * are real columns because reports aggregate on them. Immutable once published.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "quiz_version_questions",
        indexes = {
                @Index(name = "idx_qvq_version_order", columnList = "quiz_version_id, order_index"),
                @Index(name = "idx_qvq_kind", columnList = "kind")
        }
)
public class QuizVersionQuestion extends BaseEntity {

    @Column(name = "quiz_version_id", nullable = false)
    private String quizVersionId;

    @Column(name = "section_id")
    private String sectionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "runtime_question_id", nullable = false)
    private String runtimeQuestionId;

    @Column(name = "source_slide_id")
    private String sourceSlideId;

    @Column(name = "bank_question_id")
    private String bankQuestionId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "authoring_type")
    private String authoringType;

    @Column(nullable = false)
    private BigDecimal points;

    @Column(name = "is_auto_gradable", nullable = false)
    private Boolean autoGradable;

    @Column(name = "grading_policy", nullable = false)
    private String gradingPolicy;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body", columnDefinition = "jsonb")
    private Map<String, Object> body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_key", columnDefinition = "jsonb")
    private Map<String, Object> answerKey;
}
