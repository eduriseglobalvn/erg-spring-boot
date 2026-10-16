package com.erg.ergspringboot.model.curriculum;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "curriculum_topics",
        indexes = {
                @Index(name = "idx_curriculum_topics_tenant_status_sort", columnList = "tenant_id, status, sort_order"),
                @Index(name = "idx_curriculum_topics_tenant_level_status", columnList = "tenant_id, level_id, status"),
                @Index(name = "idx_curriculum_topics_tenant_code", columnList = "tenant_id, code"),
                @Index(name = "idx_curriculum_topics_tenant_updated", columnList = "tenant_id, updated_at")
        }
)
public class CurriculumTopic extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "level_id", nullable = false, length = 36)
    private String levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private CurriculumLevel level;

    @Column(nullable = false, length = 96)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState = "DRAFT";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
