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

/** Question-bank level taxonomy node (spec §1.5). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "question_bank_levels",
        indexes = {
                @Index(name = "idx_question_bank_levels_tenant_subject", columnList = "tenant_id, subject_id, order_index")
        }
)
public class QuestionBankLevel extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    private String label;

    private String description;

    @Column(name = "order_index")
    private Integer orderIndex;
}
