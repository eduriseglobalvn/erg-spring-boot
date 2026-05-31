package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class ImportJob extends BaseEntity {

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
