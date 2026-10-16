package com.erg.ergspringboot.model.quiz.bank;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Static hand-built or auto-random quiz package assembled from bank questions (spec §1.5).
 * This is a curated grouping only; published runtime quizzes live in the quiz_versions lineage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "quiz_bank_items",
        indexes = {
                @Index(name = "idx_quiz_bank_items_tenant_subject_level", columnList = "tenant_id, subject_id, level_id")
        }
)
public class QuizBankItem extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private String title;

    private String kind;

    private String status;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "level_id")
    private String levelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", columnDefinition = "jsonb")
    private List<String> questionIds;

    @Column(name = "source_mode")
    private String sourceMode;

    @Column(name = "duration_label")
    private String durationLabel;

    @Column(name = "owner_user_id")
    private String ownerUserId;
}
