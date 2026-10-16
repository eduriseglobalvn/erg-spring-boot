package com.erg.ergspringboot.model.learningoutcomes;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Durable, tenant-scoped result of evaluating a learner's path completion rule. */
@Entity
@Table(name = "learning_path_completions", indexes = {
        @Index(name = "idx_learning_path_completions_tenant_learner", columnList = "tenant_id, learner_id, evaluated_at"),
        @Index(name = "idx_learning_path_completions_tenant_path", columnList = "tenant_id, learning_path_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LearningPathCompletion extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "learner_id", nullable = false, length = 96)
    private String learnerId;

    @Column(name = "learning_path_id", nullable = false, length = 96)
    private String learningPathId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "required_node_count", nullable = false)
    private Integer requiredNodeCount;

    @Column(name = "completed_node_count", nullable = false)
    private Integer completedNodeCount;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent;

    @Column(name = "evidence_ref", nullable = false, length = 255)
    private String evidenceRef;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "evaluated_by", nullable = false, length = 96)
    private String evaluatedBy;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
