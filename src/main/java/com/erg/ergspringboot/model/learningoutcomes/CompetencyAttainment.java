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

@Entity
@Table(name = "competency_attainments", indexes = {
        @Index(name = "idx_competency_attainments_tenant_learner", columnList = "tenant_id, learner_id, achieved_at"),
        @Index(name = "idx_competency_attainments_tenant_competency", columnList = "tenant_id, competency_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CompetencyAttainment extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "learner_id", nullable = false, length = 96)
    private String learnerId;

    @Column(name = "competency_id", nullable = false, length = 96)
    private String competencyId;

    @Column(name = "learning_path_id", length = 96)
    private String learningPathId;

    @Column(name = "class_id", length = 96)
    private String classId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "evidence_ref", nullable = false, length = 255)
    private String evidenceRef;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 100;

    @Column(name = "achieved_at", nullable = false)
    private OffsetDateTime achievedAt;

    @Column(name = "recorded_by", nullable = false, length = 96)
    private String recordedBy;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
