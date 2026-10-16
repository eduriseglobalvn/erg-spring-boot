package com.erg.ergspringboot.model.quiz.version;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Runtime section compiled from an authoring group (spec §1.4, §3). Immutable once published. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "quiz_version_sections",
        indexes = {
                @Index(name = "idx_quiz_version_sections_version", columnList = "quiz_version_id, order_index")
        }
)
public class QuizVersionSection extends BaseEntity {

    @Column(name = "quiz_version_id", nullable = false)
    private String quizVersionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private String title;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}
