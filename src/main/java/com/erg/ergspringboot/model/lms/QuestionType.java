package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "question_types",
        indexes = {
                @Index(name = "idx_question_types_runtime_kind", columnList = "runtime_kind")
        }
)
public class QuestionType extends BaseEntity {

    @Column(nullable = false)
    private String label;

    @Column(name = "authoring_type", nullable = false)
    private String authoringType;

    @Column(name = "runtime_kind", nullable = false)
    private String runtimeKind;

    @Column(name = "grading_policy", nullable = false)
    private String gradingPolicy;

    @Column(name = "is_auto_gradable", nullable = false)
    private Boolean autoGradable;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private String status;
}
