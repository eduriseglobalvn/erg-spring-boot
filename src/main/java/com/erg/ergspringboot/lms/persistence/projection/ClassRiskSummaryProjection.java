package com.erg.ergspringboot.lms.persistence.projection;

public record ClassRiskSummaryProjection(
        Long missingOverdueStudentCount,
        Long lowScoreStudentCount,
        Long inactiveStudentCount
) {
}
