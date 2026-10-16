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

/** Question-bank subject taxonomy node (spec §1.5). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "question_bank_subjects",
        indexes = {
                @Index(name = "idx_question_bank_subjects_tenant", columnList = "tenant_id, label")
        }
)
public class QuestionBankSubject extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private String label;

    private String description;

    @Column(name = "company_scope_label")
    private String companyScopeLabel;
}
