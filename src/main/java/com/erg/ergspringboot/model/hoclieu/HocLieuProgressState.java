package com.erg.ergspringboot.model.hoclieu;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "hoclieu_progress_state",
        indexes = {
                @Index(name = "idx_hoclieu_progress_state_lesson", columnList = "tenant_id, user_id, school_id, academic_year, lesson_id"),
                @Index(name = "idx_hoclieu_progress_state_resource", columnList = "tenant_id, user_id, school_id, academic_year, resource_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hoclieu_progress_state_asset_scope",
                columnNames = {"tenant_id", "user_id", "school_id", "academic_year", "subject_id", "lesson_id", "resource_id", "asset_id"}
        )
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class HocLieuProgressState extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 96, nullable = false)
    private String userId;

    @Column(name = "school_id", length = 96, nullable = false)
    private String schoolId;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "subject_id", length = 96, nullable = false)
    private String subjectId;

    @Column(name = "group_id", length = 96)
    private String groupId;

    @Column(name = "lesson_id", length = 96, nullable = false)
    private String lessonId;

    @Column(name = "resource_id", length = 96, nullable = false)
    private String resourceId;

    @Column(name = "asset_id", length = 96, nullable = false)
    private String assetId;

    @Column(name = "progress_rate")
    private Integer progressRate;
}
