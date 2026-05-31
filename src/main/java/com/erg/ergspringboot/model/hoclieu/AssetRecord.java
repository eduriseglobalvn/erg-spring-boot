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
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/hoclieu/domain/entity/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "hoclieu_assets",
        indexes = {
                @Index(name = "idx_hoclieu_assets_resource", columnList = "resource_id"),
                @Index(name = "idx_hoclieu_assets_status", columnList = "status")
        }
)
@AttributeOverride(name = "id", column = @Column(name = "id", length = 96, nullable = false, updatable = false))
public class AssetRecord extends BaseEntity {

    @Column(name = "resource_id", length = 96, nullable = false)
    private String resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ResourceRecord resource;

    @Column(nullable = false)
    private String title;

    @Column(name = "selected_file_type")
    private String selectedFileType;

    @Column(name = "launch_mode")
    private String launchMode;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "detected_mime_type")
    private String detectedMimeType;

    @Column(name = "file_extension")
    private String fileExtension;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "storage_provider")
    private String storageProvider;

    @Column(name = "storage_url")
    private String storageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "google_slide_manifest", columnDefinition = "jsonb")
    private Map<String, Object> googleSlideManifest;

    @Column(name = "can_download")
    private Boolean canDownload;

    private String status;
}
