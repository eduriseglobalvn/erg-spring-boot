package com.erg.ergspringboot.curriculum.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "curriculum_class_subject_allocations")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CurriculumClassSubjectAllocation extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;
    @Column(name = "school_id", nullable = false, length = 255)
    private String schoolId;
    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;
    @Column(name = "academic_class_id", nullable = false, length = 36)
    private String academicClassId;
    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;

    /** The curriculum level this class studies the subject at (8A studies IC3 at Level 3); null when not chosen. */
    @Column(name = "level_id", length = 36)
    private String levelId;
    /** Generation of the whole (school, year) set; every row of one save carries the same value. */
    @Column(name = "version", nullable = false)
    private Long version = 1L;
    @Column(name = "created_by", length = 36)
    private String createdBy;
    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}
