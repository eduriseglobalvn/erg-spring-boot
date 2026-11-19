package com.erg.ergspringboot.lms.persistence.projection;

/** The assignment of one student's newest attempt in a class (BUG-C-013: «Bài đang làm» per student). */
public record ClassStudentCurrentAssignmentProjection(
        String studentId,
        String assignmentId,
        String assignmentTitle,
        String attemptStatus
) {
}
