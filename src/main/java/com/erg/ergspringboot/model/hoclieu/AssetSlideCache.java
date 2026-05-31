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

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "hoclieu_asset_slide_cache",
        indexes = {
                @Index(name = "idx_hoclieu_slide_cache_asset_version", columnList = "tenant_id, asset_id, source_version, slide_index"),
                @Index(name = "idx_hoclieu_slide_cache_status", columnList = "tenant_id, asset_id, status")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class AssetSlideCache extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "asset_id", length = 96, nullable = false)
    private String assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AssetRecord asset;

    @Column(name = "source_version", nullable = false)
    private String sourceVersion;

    @Column(name = "slide_index", nullable = false)
    private Integer slideIndex;

    private String title;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(nullable = false)
    private String status;
}
