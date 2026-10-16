package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.repository.lms.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.RecipientProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentCountProjection;
import com.erg.ergspringboot.repository.lms.projection.TeacherHomeworkSummaryProjection;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, String> {

    List<Assignment> findByTenantIdAndAssignedByOrderByCreatedAtDesc(String tenantId, String assignedBy);

    List<Assignment> findByTenantIdAndAcademicClassIdOrderByCreatedAtDesc(String tenantId, String academicClassId);

    List<Assignment> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query(value = """
            select matched.*
            from (
                select assignment.*
                from lms_assignments assignment
                join assignment_recipients recipient
                  on recipient.tenant_id = assignment.tenant_id
                 and recipient.assignment_id = assignment.id
                 and recipient.student_id = :studentId
                where assignment.tenant_id = :tenantId

                union all

                select assignment.*
                from lms_assignments assignment
                where assignment.tenant_id = :tenantId
                  and (
                      assignment.academic_class_id = :academicClassId
                      or assignment.student_ids @> jsonb_build_array(cast(:studentId as text))
                  )
                  and not exists (
                      select 1 from assignment_recipients any_recipient
                      where any_recipient.tenant_id = assignment.tenant_id
                        and any_recipient.assignment_id = assignment.id
                  )
            ) matched
            order by matched.created_at desc nulls last, matched.id desc
            """,
            countQuery = """
            select count(*)
            from (
                select assignment.id
                from lms_assignments assignment
                join assignment_recipients recipient
                  on recipient.tenant_id = assignment.tenant_id
                 and recipient.assignment_id = assignment.id
                 and recipient.student_id = :studentId
                where assignment.tenant_id = :tenantId

                union all

                select assignment.id
                from lms_assignments assignment
                where assignment.tenant_id = :tenantId
                  and (
                      assignment.academic_class_id = :academicClassId
                      or assignment.student_ids @> jsonb_build_array(cast(:studentId as text))
                  )
                  and not exists (
                      select 1 from assignment_recipients any_recipient
                      where any_recipient.tenant_id = assignment.tenant_id
                        and any_recipient.assignment_id = assignment.id
                  )
            ) matched
            """, nativeQuery = true)
    Page<Assignment> findStudentAssignments(
            @Param("tenantId") String tenantId,
            @Param("studentId") String studentId,
            @Param("academicClassId") String academicClassId,
            Pageable pageable
    );

    List<Assignment> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    Optional<Assignment> findByTenantIdAndId(String tenantId, String id);

    Optional<Assignment> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query("""
            select count(distinct assignment.id)
            from Assignment assignment
            left join Quiz quiz
                on quiz.tenantId = assignment.tenantId
                and quiz.id = assignment.quizId
            where assignment.tenantId = :tenantId
              and (
                  assignment.subjectId = :subjectId
                  or quiz.subjectId = :subjectId
              )
            """)
    long countImpactByTenantAndSubject(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);

    @Query("""
            select count(distinct assignment.id)
            from Assignment assignment
            join Quiz quiz
                on quiz.tenantId = assignment.tenantId
                and quiz.id = assignment.quizId
            where assignment.tenantId = :tenantId
              and quiz.levelId = :levelId
            """)
    long countImpactByTenantAndLevel(@Param("tenantId") String tenantId, @Param("levelId") String levelId);

    @Query(
            value = """
                    select count(distinct assignment.id)
                    from lms_assignments assignment
                    join lms_quizzes quiz
                      on quiz.tenant_id = assignment.tenant_id
                     and quiz.id = assignment.quiz_id
                    where assignment.tenant_id = :tenantId
                      and (
                          quiz.category_id = :topicId
                          or jsonb_exists(coalesce(quiz.topic_ids, '[]'::jsonb), :topicId)
                      )
                    """,
            nativeQuery = true
    )
    long countImpactByTenantAndTopic(@Param("tenantId") String tenantId, @Param("topicId") String topicId);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AssignmentDetailProjection(
                assignment.id,
                assignment.academicClassId,
                assignment.quizId,
                assignment.subjectId,
                assignment.dueAt,
                assignment.status,
                assignment.assignedBy,
                assignment.recipientMode,
                assignment.createdAt,
                assignment.updatedAt
            )
            from Assignment assignment
            where assignment.tenantId = :tenantId
              and assignment.id = :assignmentId
            """)
    Optional<AssignmentDetailProjection> findAssignmentDetail(String tenantId, String assignmentId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.RecipientProgressProjection(
                        student.id,
                        student.schoolId,
                        student.academicClassId,
                        student.studentCode,
                        student.fullName,
                        student.username,
                        student.authUserId,
                        student.email,
                        student.status,
                        student.updatedAt
                    )
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    where assignment.tenantId = :tenantId
                      and assignment.id = :assignmentId
                      and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
                    order by student.fullName asc, student.studentCode asc
                    """,
            countQuery = """
                    select count(student.id)
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    where assignment.tenantId = :tenantId
                      and assignment.id = :assignmentId
                      and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
                    """
    )
    Page<RecipientProgressProjection> findAssignmentRecipients(
            String tenantId,
            String assignmentId,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.MissingLateStudentProjection(
                        student.id,
                        student.schoolId,
                        student.academicClassId,
                        student.studentCode,
                        student.fullName,
                        student.username,
                        student.authUserId,
                        student.email,
                        student.status,
                        assignment.dueAt,
                        case when not exists (
                            select completedAttempt.id
                            from Attempt completedAttempt
                            where completedAttempt.tenantId = assignment.tenantId
                              and completedAttempt.assignmentId = assignment.id
                              and completedAttempt.studentId = student.id
                              and lower(completedAttempt.status) in ('submitted', 'graded', 'completed')
                        ) then true else false end,
                        case when assignment.dueAt is not null
                            and assignment.dueAt < CURRENT_TIMESTAMP
                            and not exists (
                                select onTimeAttempt.id
                                from Attempt onTimeAttempt
                                where onTimeAttempt.tenantId = assignment.tenantId
                                  and onTimeAttempt.assignmentId = assignment.id
                                  and onTimeAttempt.studentId = student.id
                                  and lower(onTimeAttempt.status) in ('submitted', 'graded', 'completed')
                                  and onTimeAttempt.submittedAt is not null
                                  and onTimeAttempt.submittedAt <= assignment.dueAt
                            )
                            then true else false end,
                        student.updatedAt
                    )
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    where assignment.tenantId = :tenantId
                      and assignment.id = :assignmentId
                      and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
                      and assignment.dueAt is not null
                      and assignment.dueAt < CURRENT_TIMESTAMP
                      and not exists (
                          select onTimeAttempt.id
                          from Attempt onTimeAttempt
                          where onTimeAttempt.tenantId = assignment.tenantId
                            and onTimeAttempt.assignmentId = assignment.id
                            and onTimeAttempt.studentId = student.id
                            and lower(onTimeAttempt.status) in ('submitted', 'graded', 'completed')
                            and onTimeAttempt.submittedAt is not null
                            and onTimeAttempt.submittedAt <= assignment.dueAt
                      )
                    order by student.fullName asc, student.studentCode asc
                    """,
            countQuery = """
                    select count(student.id)
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    where assignment.tenantId = :tenantId
                      and assignment.id = :assignmentId
                      and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
                      and assignment.dueAt is not null
                      and assignment.dueAt < CURRENT_TIMESTAMP
                      and not exists (
                          select onTimeAttempt.id
                          from Attempt onTimeAttempt
                          where onTimeAttempt.tenantId = assignment.tenantId
                            and onTimeAttempt.assignmentId = assignment.id
                            and onTimeAttempt.studentId = student.id
                            and lower(onTimeAttempt.status) in ('submitted', 'graded', 'completed')
                            and onTimeAttempt.submittedAt is not null
                            and onTimeAttempt.submittedAt <= assignment.dueAt
                      )
                    """
    )
    Page<MissingLateStudentProjection> findAssignmentMissingLateStudents(
            String tenantId,
            String assignmentId,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection(
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        assignment.dueAt,
                        assignment.status,
                        assignment.assignedBy,
                        assignment.recipientMode,
                        assignment.createdAt,
                        assignment.updatedAt
                    )
                    from Assignment assignment
                    where assignment.tenantId = :tenantId
                      and assignment.academicClassId = :academicClassId
                      and (:status is null or lower(assignment.status) = :status)
                    order by assignment.dueAt asc nulls last, assignment.createdAt desc
                    """,
            countQuery = """
                    select count(assignment.id)
                    from Assignment assignment
                    where assignment.tenantId = :tenantId
                      and assignment.academicClassId = :academicClassId
                      and (:status is null or lower(assignment.status) = :status)
                    """
    )
    Page<AssignmentCardProjection> findClassAssignmentCards(
            String tenantId,
            String academicClassId,
            String status,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection(
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        assignment.dueAt,
                        assignment.status,
                        assignment.assignedBy,
                        assignment.recipientMode,
                        assignment.createdAt,
                        assignment.updatedAt
                    )
                    from Assignment assignment
                    where assignment.tenantId = :tenantId
                      and assignment.assignedBy = :assignedBy
                      and (:status is null or lower(assignment.status) = :status)
                    order by assignment.createdAt desc
                    """,
            countQuery = """
                    select count(assignment.id)
                    from Assignment assignment
                    where assignment.tenantId = :tenantId
                      and assignment.assignedBy = :assignedBy
                      and (:status is null or lower(assignment.status) = :status)
                    """
    )
    Page<AssignmentCardProjection> findTeacherAssignmentCards(
            String tenantId,
            String assignedBy,
            String status,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.TeacherHomeworkSummaryProjection(
                count(distinct case when lower(assignment.status) = 'active' then assignment.id else null end),
                count(distinct case when lower(assignment.status) = 'active'
                    and assignment.dueAt is not null
                    and assignment.dueAt < CURRENT_TIMESTAMP
                    then assignment.id else null end),
                count(distinct case when lower(attempt.status) = 'submitted' then assignment.id else null end)
            )
            from Assignment assignment
            left join Attempt attempt
                on attempt.tenantId = assignment.tenantId
                and attempt.assignmentId = assignment.id
            where assignment.tenantId = :tenantId
              and assignment.assignedBy = :assignedBy
            """)
    TeacherHomeworkSummaryProjection summarizeTeacherHomeworkWorkspace(String tenantId, String assignedBy);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentProjection(
                assignment.id,
                assignment.academicClassId,
                assignment.quizId,
                assignment.subjectId,
                subject.name,
                coalesce(quiz.title, assignment.subjectId, assignment.id),
                assignment.dueAt,
                assignment.status,
                assignment.updatedAt,
                latestAttempt.id,
                latestAttempt.status,
                latestAttempt.percent,
                latestAttempt.submittedAt,
                latestAttempt.updatedAt
            )
            from Assignment assignment
            left join Quiz quiz
                on quiz.tenantId = assignment.tenantId
                and quiz.id = assignment.quizId
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
            """)
    List<StudentDashboardAssignmentProjection> findStudentDashboardAssignments(
            String tenantId,
            String academicClassId,
            String studentId,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentCountProjection(
                count(distinct case when lower(assignment.status) = 'active' then assignment.id else null end),
                count(distinct case when lower(assignment.status) = 'active'
                    and not exists (
                        select attempt.id
                        from Attempt attempt
                        where attempt.tenantId = assignment.tenantId
                          and attempt.assignmentId = assignment.id
                          and attempt.studentId = :studentId
                          and lower(attempt.status) in ('submitted', 'graded', 'completed')
                    ) then assignment.id else null end)
            )
            from Assignment assignment
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
            """)
    StudentDashboardAssignmentCountProjection summarizeStudentDashboardAssignments(
            String tenantId,
            String academicClassId,
            String studentId
    );

    @Query("""
            select count(distinct assignment.id)
            from Assignment assignment
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
              and lower(assignment.status) = 'active'
            """)
    long countActiveStudentAssignments(String tenantId, String academicClassId, String studentId);

    @Query("""
            select count(distinct assignment.id)
            from Assignment assignment
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
              and lower(assignment.status) = 'active'
              and not exists (
                  select attempt.id
                  from Attempt attempt
                  where attempt.tenantId = assignment.tenantId
                    and attempt.assignmentId = assignment.id
                    and attempt.studentId = :studentId
                    and lower(attempt.status) in ('submitted', 'graded', 'completed')
              )
            """)
    long countPendingStudentAssignmentActions(String tenantId, String academicClassId, String studentId);
}
