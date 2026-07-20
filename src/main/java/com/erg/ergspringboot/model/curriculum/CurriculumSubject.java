package com.erg.ergspringboot.model.curriculum;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
        name = "curriculum_subjects",
        indexes = {
                @Index(name = "idx_curriculum_subjects_tenant_status_sort", columnList = "tenant_id, status, sort_order"),
                @Index(name = "idx_curriculum_subjects_tenant_code", columnList = "tenant_id, code"),
                @Index(name = "idx_curriculum_subjects_tenant_updated", columnList = "tenant_id, updated_at")
        }
)
public class CurriculumSubject extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

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

    @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<CurriculumLevel> levels = new LinkedHashSet<>();
}
