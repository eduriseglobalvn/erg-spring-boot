package com.erg.ergspringboot.model.quiz.media;

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
 * Content-addressed media asset (spec §1.6, §5). Deduplicated per (tenant, sha256); slides reference
 * this by id/url instead of embedding base64. {@code status} moves pending -> committed once the bytes
 * are verified. {@code refCount} drives orphan GC.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "media_assets",
        indexes = {
                @Index(name = "idx_media_assets_tenant_status", columnList = "tenant_id, status")
        }
)
public class MediaAsset extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "storage_key")
    private String storageKey;

    private String url;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "byte_size")
    private Long byteSize;

    private Integer width;

    private Integer height;

    @Column(name = "original_name")
    private String originalName;

    @Column(nullable = false)
    private String status;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "ref_count", nullable = false)
    private Integer refCount;
}
