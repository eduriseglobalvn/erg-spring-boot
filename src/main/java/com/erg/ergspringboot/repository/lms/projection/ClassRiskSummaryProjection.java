package com.erg.ergspringboot.repository.lms.projection;

public record ClassRiskSummaryProjection(
        Long missingOverdueStudentCount,
        Long lowScoreStudentCount,
        Long inactiveStudentCount
) {
}
