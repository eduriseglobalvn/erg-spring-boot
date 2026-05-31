package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentScope {

    private String id;

    private String tenantId;

    private String userId;

    private String level;

    private String educationUnitId;

    private String academicClassId;

    private OffsetDateTime updatedAt;
}
