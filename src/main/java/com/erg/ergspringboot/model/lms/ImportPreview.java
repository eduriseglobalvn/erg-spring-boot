package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreview {

    private String id;

    private String tenantId;

    private String userId;

    private String spreadsheetId;

    private String sheetName;

    private String range;

    private List<ParsedStudentRow> rows;

    private OffsetDateTime createdAt;
}
