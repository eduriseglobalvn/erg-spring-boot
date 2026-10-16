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
@Table(name = "curriculum_program_rows", indexes = {
        @Index(name = "idx_curriculum_program_rows_order", columnList = "tenant_id, program_id, sort_order, period")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CurriculumProgramRow extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 255) private String tenantId;
    @Column(name = "program_id", nullable = false, length = 36) private String programId;
    @Column(nullable = false) private String topic;
    @Column(nullable = false) private Integer period;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false) private Integer sessions = 1;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder;
    @Column(nullable = false, length = 32) private String status;
    @Version @Column(name = "record_version", nullable = false) private Long recordVersion = 0L;
}
