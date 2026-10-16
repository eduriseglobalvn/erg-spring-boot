package com.erg.ergspringboot.model.school;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "school_grades")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SchoolGrade extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
    @Column(name = "school_id", nullable = false, length = 36)
    private String schoolId;
    @Column(name = "academic_year_id", nullable = false, length = 36)
    private String academicYearId;
    @Column(name = "grade_code", nullable = false)
    private String gradeCode;
    @Column(nullable = false)
    private String label;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String source;
}
