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
public class StudentMetrics {

    private Double averageScore;

    private Integer completedAssignments;

    private OffsetDateTime lastActivityAt;
}
