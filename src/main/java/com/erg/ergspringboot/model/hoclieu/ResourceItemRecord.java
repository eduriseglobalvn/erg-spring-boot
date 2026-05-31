package com.erg.ergspringboot.model.hoclieu;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
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
 * Migrated from erg-go internal/modules/hoclieu/domain/entity/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "hoclieu_resource_items",
        indexes = {
                @Index(name = "idx_hoclieu_items_tenant_resource", columnList = "tenant_id, resource_id"),
                @Index(name = "idx_hoclieu_items_asset", columnList = "asset_id")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class ResourceItemRecord extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "resource_id", length = 96, nullable = false)
    private String resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ResourceRecord resource;

    @Column(name = "asset_id", length = 96)
    private String assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AssetRecord asset;

    @Column(name = "unit_title")
    private String unitTitle;

    @Column(name = "lesson_title")
    private String lessonTitle;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "duration_sec")
    private Integer durationSec;
}
