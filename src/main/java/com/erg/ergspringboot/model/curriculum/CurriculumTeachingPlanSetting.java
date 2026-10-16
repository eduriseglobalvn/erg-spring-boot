package com.erg.ergspringboot.model.curriculum;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "curriculum_teaching_plan_settings", indexes = {
        @Index(name = "idx_curriculum_teaching_plan_scope", columnList = "tenant_id, school_id, subject_id, academic_year")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CurriculumTeachingPlanSetting extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;
    @Column(name = "school_id", nullable = false, length = 36)
    private String schoolId;
    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;
    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;
    @Column(name = "start_date", nullable = false)
    private java.time.LocalDate startDate;
    @Column(name = "teaching_days", nullable = false, length = 32)
    private String teachingDays;
    @Column(name = "sessions_per_day", nullable = false)
    private Integer sessionsPerDay;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Column(nullable = false, length = 32)
    private String status;
    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
    @Column(name = "created_by", length = 36)
    private String createdBy;
    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}
