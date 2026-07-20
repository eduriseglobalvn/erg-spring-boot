package com.erg.ergspringboot.model.lms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One quiz within a multi-quiz assignment (spec §1.8, §4.8). Each row pins the published
 * {@code quiz_version_id} active at assign time so recipients keep a stable version even if the quiz
 * is republished later. Composite PK (assignment_id, quiz_id).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(AssignmentResourceId.class)
@Table(
        name = "assignment_resources",
        indexes = {
                @Index(name = "idx_assignment_resources_tenant_quiz", columnList = "tenant_id, quiz_id")
        }
)
public class AssignmentResource {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private String assignmentId;

    @Id
    @Column(name = "quiz_id", nullable = false)
    private String quizId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "quiz_version_id")
    private String quizVersionId;

    @Column(name = "quiz_version_label")
    private String quizVersionLabel;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
