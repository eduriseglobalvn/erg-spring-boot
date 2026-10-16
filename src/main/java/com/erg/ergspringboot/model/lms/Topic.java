package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private Level level;

    private String name;

    private String code;

    @Column(name = "sort_order")
    private Integer order;

    private String status;
}
