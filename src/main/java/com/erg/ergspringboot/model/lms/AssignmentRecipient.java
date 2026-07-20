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

/**
 * Flattened per-student recipient of an assignment (spec §1.8, §4.7). Whatever the recipient mode
 * (class / group / explicit students), it resolves to one row per student here so read paths never
 * re-expand a class. {@code sourceMode} records how the student was added for audit/UI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "assignment_recipients",
        indexes = {
                @Index(name = "idx_assignment_recipients_tenant_student", columnList = "tenant_id, student_id"),
                @Index(name = "idx_assignment_recipients_tenant_class", columnList = "tenant_id, class_id")
        }
)
public class AssignmentRecipient extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private String assignmentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_user_id")
    private String studentUserId;

    @Column(name = "class_id")
    private String classId;

    @Column(name = "source_mode", nullable = false)
    private String sourceMode;
}
