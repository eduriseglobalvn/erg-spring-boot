package com.erg.ergspringboot.lms.persistence.projection;

/** One student's score in a class: their latest hand-in per assignment, averaged. */
public record ClassStudentScoreProjection(
        String studentId,
        Long completedAssignmentCount,
        Double averagePercent
) {
}
