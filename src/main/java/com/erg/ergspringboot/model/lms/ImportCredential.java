package com.erg.ergspringboot.model.lms;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportCredential {

    private String rowId;

    private Integer rowNumber;

    private String studentId;

    private String username;

    private String password;
}
