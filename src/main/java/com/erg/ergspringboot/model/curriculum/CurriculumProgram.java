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
@Table(name = "curriculum_programs", indexes = {
        @Index(name = "idx_curriculum_program_scope", columnList = "tenant_id, subject_id, level_id, academic_year, status")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CurriculumProgram extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 255) private String tenantId;
    @Column(name = "subject_id", nullable = false, length = 36) private String subjectId;
    @Column(name = "level_id", nullable = false, length = 36) private String levelId;
    @Column(name = "academic_year", nullable = false, length = 16) private String academicYear;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 32) private String status;
    @Version @Column(name = "record_version", nullable = false) private Long recordVersion = 0L;
    @Column(name = "created_by", length = 36) private String createdBy;
    @Column(name = "updated_by", length = 36) private String updatedBy;
}
