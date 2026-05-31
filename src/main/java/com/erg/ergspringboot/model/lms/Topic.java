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
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "lms_topics",
        indexes = {
                @Index(name = "idx_lms_topics_tenant_level", columnList = "tenant_id, level_id"),
                @Index(name = "idx_lms_topics_tenant_status", columnList = "tenant_id, status")
        }
)
public class Topic extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "level_id")
    private String levelId;

    private String name;

    private String code;

    @Column(name = "sort_order")
    private Integer order;

    private String status;
}
