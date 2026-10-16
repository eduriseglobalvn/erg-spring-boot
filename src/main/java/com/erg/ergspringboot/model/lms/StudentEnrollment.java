package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Current and historical class membership for a student. */
@Entity
@Table(name = "lms_student_enrollments", indexes = {
        @Index(name = "idx_lms_enrollment_tenant_school", columnList = "tenant_id, school_id"),
        @Index(name = "idx_lms_enrollment_tenant_student", columnList = "tenant_id, student_id"),
        @Index(name = "idx_lms_enrollment_tenant_class", columnList = "tenant_id, class_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class StudentEnrollment extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "class_id", nullable = false)
    private String classId;

    @Column(name = "academic_year", length = 64)
    private String academicYear;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;
}
