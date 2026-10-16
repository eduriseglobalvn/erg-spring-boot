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
 * Named ad-hoc cohort used as an assignment recipient (spec §1.8). Members live in
 * {@code student_group_members}; {@code studentCount} is a denormalized convenience count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "student_groups",
        indexes = {
                @Index(name = "idx_student_groups_tenant", columnList = "tenant_id, class_id")
        }
)
public class StudentGroup extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private String name;

    @Column(name = "class_id")
    private String classId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(length = 64)
    private String color;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "student_count", nullable = false)
    private Integer studentCount;
}
