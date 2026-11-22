package com.erg.ergspringboot.lms.persistence;

/**
 * JPQL of the assignment progress recipient lists. The student set is driven from the saved recipient rows, or, for an assignment
 * without rows (BUG-D-003), from the class roster of a class-mode assignment: never from every student of the tenant.
 */
final class AssignmentRecipientProgressSql {
    private static final String PROJECTION = "student.id, student.schoolId, student.academicClassId, student.studentCode, "
            + "student.fullName, student.username, student.authUserId, student.email, student.status, ";

    static final String RECIPIENT_SELECT = "select new com.erg.ergspringboot.lms.persistence.projection.RecipientProgressProjection("
            + PROJECTION + "student.updatedAt) ";

    static final String MISSING_SELECT = "select new com.erg.ergspringboot.lms.persistence.projection.MissingLateStudentProjection("
            + PROJECTION + """
            assignment.dueAt,
            case when not exists (
                select 1 from Attempt completedAttempt
                where completedAttempt.tenantId = assignment.tenantId
                  and completedAttempt.assignmentId = assignment.id
                  and completedAttempt.studentId = student.id
                  and lower(completedAttempt.status) in :completedStates
            ) then true else false end,
            case when assignment.dueAt is not null and assignment.dueAt < CURRENT_TIMESTAMP and not exists (
                select 1 from Attempt onTimeAttempt
                where onTimeAttempt.tenantId = assignment.tenantId
                  and onTimeAttempt.assignmentId = assignment.id
                  and onTimeAttempt.studentId = student.id
                  and lower(onTimeAttempt.status) in :completedStates
                  and onTimeAttempt.submittedAt is not null
                  and onTimeAttempt.submittedAt <= assignment.dueAt
            ) then true else false end,
            student.updatedAt)\s
            """;

    static final String FROM_ROWS = """
            from AssignmentRecipient recipient
            join Assignment assignment on assignment.tenantId = recipient.tenantId and assignment.id = recipient.assignmentId
            join Student student on student.tenantId = recipient.tenantId and student.id = recipient.studentId
            where recipient.tenantId = :tenantId and recipient.assignmentId = :assignmentId
            """;

    static final String FROM_ROSTER = """
            from Assignment assignment
            join Student student on student.tenantId = assignment.tenantId and student.academicClassId = assignment.academicClassId
            where assignment.tenantId = :tenantId and assignment.id = :assignmentId
              and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
            """;

    static final String FROM_STUDENTS = """
            from Assignment assignment
            join Student student on student.tenantId = assignment.tenantId
            where assignment.tenantId = :tenantId and assignment.id = :assignmentId and student.id in :studentIds
            """;

    /** Overdue and no on-time completed attempt: the rows of the missing/late list. */
    static final String MISSING_LATE_FILTER = """
              and assignment.dueAt is not null
              and assignment.dueAt < CURRENT_TIMESTAMP
              and not exists (
                  select 1 from Attempt onTimeAttempt
                  where onTimeAttempt.tenantId = assignment.tenantId
                    and onTimeAttempt.assignmentId = assignment.id
                    and onTimeAttempt.studentId = student.id
                    and lower(onTimeAttempt.status) in :completedStates
                    and onTimeAttempt.submittedAt is not null
                    and onTimeAttempt.submittedAt <= assignment.dueAt)
            """;

    static final String ORDER = "order by student.fullName asc, student.studentCode asc";
    static final String COUNT = "select count(student.id) ";

    private AssignmentRecipientProgressSql() {
    }
}
