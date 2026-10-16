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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "lms_subjects",
        indexes = {
                @Index(name = "idx_lms_subjects_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_lms_subjects_tenant_code", columnList = "tenant_id, code")
        }
)
public class Subject extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb")
    private ContentScope scope;

    private String name;

    private String code;

    private String status;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;
}
