package com.erg.ergspringboot.model.learningcontent;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "content_item_assets",
        indexes = {
                @Index(name = "idx_content_item_assets_item_sort", columnList = "tenant_id, content_item_id, sort_order"),
                @Index(name = "idx_content_item_assets_asset", columnList = "tenant_id, content_asset_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_content_item_assets_role",
                        columnNames = {"tenant_id", "content_item_id", "content_asset_id", "asset_role"})
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class ContentItemAsset extends BaseEntity {

    @Column(name = "tenant_id", length = 96, nullable = false)
    private String tenantId;

    @Column(name = "content_item_id", length = 96, nullable = false)
    private String contentItemId;

    @Column(name = "content_asset_id", length = 96, nullable = false)
    private String contentAssetId;

    @Column(name = "asset_role", length = 64, nullable = false)
    private String assetRole;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_by", length = 96)
    private String createdBy;
}
