package com.erg.ergspringboot.model.lms;

import java.util.List;
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
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class ImportJob {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String userId;

    private String previewId;

    private String spreadsheetId;

    private String sheetName;

    private String sheetRange;

    private String status;

    private Integer progress;

    private Integer created;

    private Integer skipped;

    private Integer duplicates;

    private List<ImportCredential> credentials;

    private List<String> errors;
}
