package com.erg.ergspringboot.model.documents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/documents/domain/entity/document.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Document {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String filename;

    private String originalName;

    private String mimeType;

    private Long size;

    private String storageType;

    private String r2Url;

    private String driveId;

    private WatermarkConfig watermarkConfig;

    private String status;

    private String uploadedBy;
}
