package com.erg.ergspringboot.model.quiz.bank;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Question-bank category taxonomy node (spec §1.5). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "question_bank_categories",
        indexes = {
                @Index(name = "idx_question_bank_categories_tenant_scope", columnList = "tenant_id, subject_id, level_id")
        }
)
public class QuestionBankCategory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "level_id", nullable = false)
    private String levelId;

    private String label;
}
