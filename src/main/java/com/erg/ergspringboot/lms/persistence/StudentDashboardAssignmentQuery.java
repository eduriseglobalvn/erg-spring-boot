package com.erg.ergspringboot.lms.persistence;

/** The student dashboard assignment list query, kept out of {@link AssignmentRepository} to hold its size ceiling. */
final class StudentDashboardAssignmentQuery {
    static final String ASSIGNMENTS = """
            select new com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentProjection(
                assignment.id,
                assignment.academicClassId,
                assignment.quizId,
                assignment.subjectId,
                subject.name,
                coalesce(nullif(assignment.title, ''), quiz.title, assignment.subjectId, assignment.id),
                assignment.dueAt,
                assignment.status,
                assignment.updatedAt,
                latestAttempt.id,
                latestAttempt.status,
                latestAttempt.percent,
                latestAttempt.submittedAt,
                latestAttempt.updatedAt,
                handedIn.percent,
                handedIn.submittedAt,
                assignment.maxDurationMinutes,
                teacher.fullName,
                quiz.kind,
                cast(case when function('jsonb_typeof', quiz.questionIds) = 'array'
                    then function('jsonb_array_length', quiz.questionIds) else null end as integer),
                (select count(done.id) from Attempt done
                    where done.tenantId = assignment.tenantId and done.assignmentId = assignment.id
                      and done.studentId = :studentId and lower(done.status) in :completedStates),
                handedIn.score,
                handedIn.maxScore,
                handedIn.totalQuestions,
                handedIn.late,
                latestAttempt.submittedCount,
                latestAttempt.totalQuestions,
                (select case when count(ans.attemptId) = 0 then null
                        else sum(case when ans.correct = true then 1 else 0 end) end
                    from AttemptAnswerRow ans where ans.tenantId = handedIn.tenantId and ans.attemptId = handedIn.id)
            )
            from Assignment assignment
            left join Quiz quiz
                on quiz.tenantId = assignment.tenantId
                and quiz.id = assignment.quizId
            left join User teacher
                on teacher.id = assignment.assignedBy
            left join Subject subject
                on subject.tenantId = assignment.tenantId
                and subject.id = assignment.subjectId
            left join Attempt latestAttempt
                on latestAttempt.tenantId = assignment.tenantId
                and latestAttempt.assignmentId = assignment.id
                and latestAttempt.studentId = :studentId
                and latestAttempt.updatedAt = (
                    select max(attempt.updatedAt)
                    from Attempt attempt
                    where attempt.tenantId = assignment.tenantId
                      and attempt.assignmentId = assignment.id
                      and attempt.studentId = :studentId
                )
            left join Attempt handedIn on handedIn.tenantId = assignment.tenantId and handedIn.assignmentId = assignment.id
                and handedIn.studentId = :studentId and lower(handedIn.status) in :completedStates
            """ + LatestHandedInAttempt.OF_HANDED_IN + """
            left join AssignmentRecipient recipient
                on recipient.tenantId = assignment.tenantId
                and recipient.assignmentId = assignment.id
                and recipient.studentId = :studentId
            where assignment.tenantId = :tenantId
              and (
                  (
                      recipient.id is not null
                      and (
                          :academicClassId is null
                          or recipient.classId = :academicClassId
                          or assignment.academicClassId = :academicClassId
                      )
                  )
                  or (
                      :academicClassId is not null
                      and assignment.academicClassId = :academicClassId
                      and not exists (
                          select anyRecipient.id
                          from AssignmentRecipient anyRecipient
                          where anyRecipient.tenantId = assignment.tenantId
                            and anyRecipient.assignmentId = assignment.id
                      )
                  )
              )
            order by assignment.dueAt asc nulls last, assignment.updatedAt desc
            """;

    private StudentDashboardAssignmentQuery() {
    }
}
