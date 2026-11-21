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

    /** Every lms_assignments column the list needs; the student_ids snapshot is replaced by null so it never leaves the database. */
    private static final String LIST_COLUMNS = """
            assignment.id, assignment.created_at, assignment.updated_at, assignment.tenant_id, assignment.academic_class_id,
            assignment.quiz_id, assignment.subject_id, cast(null as jsonb) as student_ids, assignment.due_at,
            assignment.teacher_note, assignment.status, assignment.assigned_by, assignment.recipient_mode, assignment.title,
            assignment.attempt_limit, assignment.max_duration_minutes, assignment.offline_allowed, assignment.start_at,
            assignment.idempotency_key, assignment.recipient_count, assignment.record_version
            """;

    /**
     * Native branches of the student assignment list (alias assignment; params tenantId, studentId, academicClassId). Saved recipient
     * rows win; an assignment without rows falls back to the class, then to the legacy student_ids snapshot. The OR is split into
     * two UNION ALL branches (the second excludes the class) so the class index and idx_lms_assignments_student_ids_gin apply.
     */
    static final String STUDENT_ASSIGNMENTS_SQL = """
            select\s""" + LIST_COLUMNS + """
            from lms_assignments assignment
            join assignment_recipients recipient
              on recipient.tenant_id = assignment.tenant_id
             and recipient.assignment_id = assignment.id
             and recipient.student_id = :studentId
            where assignment.tenant_id = :tenantId

            union all

            select\s""" + LIST_COLUMNS + """
            from lms_assignments assignment
            where assignment.tenant_id = :tenantId
              and assignment.academic_class_id = :academicClassId
              and not exists (select 1 from assignment_recipients r
                              where r.tenant_id = assignment.tenant_id and r.assignment_id = assignment.id)

            union all

            select\s""" + LIST_COLUMNS + """
            from lms_assignments assignment
            where assignment.tenant_id = :tenantId
              and assignment.student_ids @> jsonb_build_array(cast(:studentId as text))
              and assignment.academic_class_id is distinct from cast(:academicClassId as varchar)
              and not exists (select 1 from assignment_recipients r
                              where r.tenant_id = assignment.tenant_id and r.assignment_id = assignment.id)
            """;

    /**
     * One exists for "a visible assignment distributes this quiz to this learner": the same recipient rule as
     * {@link #STUDENT_ASSIGNMENTS_SQL} and {@code LmsAssignmentAccessPolicy.isRecipient} (recipient rows win; without rows the
     * class or the legacy student_ids snapshot), the same visibility as {@code LmsStatePolicy.assignmentIsVisible}.
     * Params tenantId, quizId, assignmentId (null = any assignment), studentId, academicClassId, authUserId (null when unlinked).
     */
    static final String VISIBLE_ASSIGNMENT_PREDICATE = """
            where assignment.tenant_id = :tenantId
                  and lower(assignment.status) in ('assigned', 'active', 'published')
                  and (cast(:assignmentId as varchar) is null or assignment.id = cast(:assignmentId as varchar))
                  and (assignment.quiz_id = :quizId
                       or exists (select 1 from assignment_resources resource
                                  where resource.tenant_id = assignment.tenant_id
                                    and resource.assignment_id = assignment.id and resource.quiz_id = :quizId))
                  and (exists (select 1 from assignment_recipients recipient
                               where recipient.tenant_id = assignment.tenant_id
                                 and recipient.assignment_id = assignment.id
                                 and recipient.student_id = cast(:studentId as varchar))
                       or (not exists (select 1 from assignment_recipients r
                                       where r.tenant_id = assignment.tenant_id and r.assignment_id = assignment.id)
                           and (assignment.academic_class_id = cast(:academicClassId as varchar)
                                or (cast(:studentId as text) is not null
                                    and assignment.student_ids @> jsonb_build_array(cast(:studentId as text)))
                                or (cast(:authUserId as text) is not null
                                    and assignment.student_ids @> jsonb_build_array(cast(:authUserId as text))))))
            """;

    static final String VISIBLE_QUIZ_FOR_LEARNER_SQL = "select exists (select 1 from lms_assignments assignment " + VISIBLE_ASSIGNMENT_PREDICATE + ")";

    /** The assignments {@link #VISIBLE_QUIZ_FOR_LEARNER_SQL} accepts, for the offline import of a result file (same params). */
    static final String VISIBLE_ASSIGNMENT_IDS_SQL = "select assignment.id from lms_assignments assignment " + VISIBLE_ASSIGNMENT_PREDICATE;

    private AssignmentRecipientScope() {
    }
}
