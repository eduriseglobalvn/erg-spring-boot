package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "academic_classes",
        indexes = {
                @Index(name = "idx_academic_classes_tenant_school", columnList = "tenant_id, school_id"),
                @Index(name = "idx_academic_classes_tenant_teacher", columnList = "tenant_id, homeroom_teacher_id"),
                @Index(name = "idx_academic_classes_tenant_status", columnList = "tenant_id, status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Classroom extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    private String name;

    private String grade;

    @Column(name = "academic_year")
    private String academicYear;

    private String status;

    @Column(name = "homeroom_teacher_id")
    private String homeroomTeacherId;
}
