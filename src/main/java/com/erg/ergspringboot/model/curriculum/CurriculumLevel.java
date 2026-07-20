package com.erg.ergspringboot.model.curriculum;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "curriculum_levels",
        indexes = {
                @Index(name = "idx_curriculum_levels_tenant_status_sort", columnList = "tenant_id, status, sort_order"),
                @Index(name = "idx_curriculum_levels_tenant_subject_status", columnList = "tenant_id, subject_id, status"),
                @Index(name = "idx_curriculum_levels_tenant_code", columnList = "tenant_id, code"),
                @Index(name = "idx_curriculum_levels_tenant_updated", columnList = "tenant_id, updated_at")
        }
)
public class CurriculumLevel extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", referencedColumnName = "id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CurriculumSubject subject;

    @Column(nullable = false, length = 96)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<CurriculumTopic> topics = new LinkedHashSet<>();
}
