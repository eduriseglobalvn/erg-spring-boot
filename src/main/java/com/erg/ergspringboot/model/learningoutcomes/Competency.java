package com.erg.ergspringboot.model.learningoutcomes;

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

@Entity
@Table(name = "competencies", indexes = {
        @Index(name = "idx_competencies_tenant_status", columnList = "tenant_id, status, updated_at"),
        @Index(name = "idx_competencies_tenant_scope", columnList = "tenant_id, education_unit_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Competency extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "code", nullable = false, length = 96)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "education_unit_id", length = 96)
    private String educationUnitId;

    @Column(name = "created_by", nullable = false, length = 96)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 96)
    private String updatedBy;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
