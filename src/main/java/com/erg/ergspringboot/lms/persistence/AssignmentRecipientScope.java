package com.erg.ergspringboot.lms.persistence;

/** JPQL for "student is a recipient of the assignment": saved recipient rows, else the class roster (BUG-D-003). Needs aliases assignment and student. */
final class AssignmentRecipientScope {
    static final String RECIPIENT_OF_ASSIGNMENT = """
            and (exists (
                    select 1 from AssignmentRecipient recipient
                    where recipient.tenantId = assignment.tenantId
                      and recipient.assignmentId = assignment.id
                      and recipient.studentId = student.id)
                or (student.academicClassId = assignment.academicClassId
                    and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
                    and not exists (
                        select 1 from AssignmentRecipient anyRecipient
                        where anyRecipient.tenantId = assignment.tenantId
                          and anyRecipient.assignmentId = assignment.id)))
            """;

    private AssignmentRecipientScope() {
    }
}
