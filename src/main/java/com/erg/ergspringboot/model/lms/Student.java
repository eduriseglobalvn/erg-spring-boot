package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "lms_students",
        indexes = {
                @Index(name = "idx_lms_students_tenant_school", columnList = "tenant_id, school_id"),
                @Index(name = "idx_lms_students_tenant_academic_class", columnList = "tenant_id, academic_class_id"),
                @Index(name = "idx_lms_students_tenant_auth_user", columnList = "tenant_id, auth_user_id"),
                @Index(name = "idx_lms_students_tenant_code", columnList = "tenant_id, student_code")
        }
)
public class Student extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "school_id")
    private String schoolId;

    @Column(name = "academic_class_id")
    private String academicClassId;

    @Column(name = "student_code")
    private String studentCode;

    @Column(name = "full_name")
    private String fullName;

    private String username;

    @Column(name = "auth_user_id")
    private String authUserId;

    private String email;

    private String gender;

    private OffsetDateTime birthday;

    private String phone;

    private String address;

    @Column(name = "parent_name")
    private String parentName;

    @Column(name = "parent_phone")
    private String parentPhone;

    @Column(name = "parent_email")
    private String parentEmail;

    @Column(name = "parent_relationship")
    private String parentRelationship;

    @Column(name = "enrollment_date")
    private OffsetDateTime enrollmentDate;

    private String note;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private StudentMetrics metrics;
}
