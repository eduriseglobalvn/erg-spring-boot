package com.erg.ergspringboot.model.lms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Membership of a student in a {@link StudentGroup} (spec §1.8). Composite PK (groupId, studentId). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(StudentGroupMemberId.class)
@Table(
        name = "student_group_members",
        indexes = {
                @Index(name = "idx_student_group_members_tenant_student", columnList = "tenant_id, student_id")
        }
)
public class StudentGroupMember {

    @Id
    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Id
    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
