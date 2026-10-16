package com.erg.ergspringboot.model.learningcontent;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "content_assets",
        indexes = {
                @Index(name = "idx_content_assets_tenant_status", columnList = "tenant_id, status, updated_at"),
                @Index(name = "idx_content_assets_tenant_checksum", columnList = "tenant_id, checksum_sha256")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class ContentAsset extends BaseEntity {

    @Column(name = "tenant_id", length = 96, nullable = false)
    private String tenantId;

    @Column(name = "asset_type", length = 64, nullable = false)
    private String assetType;

    @Column(length = 500)
    private String title;

    @Column(name = "storage_provider", length = 64)
    private String storageProvider;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;

    @Column(name = "storage_url", columnDefinition = "text")
    private String storageUrl;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_by", length = 96)
    private String createdBy;

    @Column(name = "updated_by", length = 96)
    private String updatedBy;

    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;
}
