package com.erg.ergspringboot.model.documents;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class Document extends BaseEntity {

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
