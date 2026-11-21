package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.AssignmentCardProjection;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentCountProjection;
import com.erg.ergspringboot.lms.persistence.projection.TeacherHomeworkSummaryProjection;
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

    List<Assignment> findByTenantIdAndQuizId(String tenantId, String quizId);

    @Query(value = "select matched.* from (" + AssignmentRecipientScope.STUDENT_ASSIGNMENTS_SQL
            + ") matched order by matched.created_at desc nulls last, matched.id desc",
            countQuery = "select count(*) from (" + AssignmentRecipientScope.STUDENT_ASSIGNMENTS_SQL + ") matched",
            nativeQuery = true)
    Page<Assignment> findStudentAssignments(
            @Param("tenantId") String tenantId,
            @Param("studentId") String studentId,
            @Param("academicClassId") String academicClassId,
            Pageable pageable
    );

    @Query(value = AssignmentRecipientScope.VISIBLE_QUIZ_FOR_LEARNER_SQL, nativeQuery = true)
    boolean existsVisibleQuizForLearner(
            @Param("tenantId") String tenantId,
            @Param("quizId") String quizId,
            @Param("assignmentId") String assignmentId,
            @Param("studentId") String studentId,
            @Param("academicClassId") String academicClassId,
            @Param("authUserId") String authUserId
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
            select new com.erg.ergspringboot.lms.persistence.projection.AssignmentDetailProjection(
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
                    select new com.erg.ergspringboot.lms.persistence.projection.AssignmentCardProjection(
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        assignment.dueAt,
                        assignment.status,
                        assignment.assignedBy,
                        assignment.recipientMode,
                        assignment.createdAt,
                        assignment.updatedAt,
                        assignment.title
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
                    select new com.erg.ergspringboot.lms.persistence.projection.AssignmentCardProjection(
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        assignment.dueAt,
                        assignment.status,
                        assignment.assignedBy,
                        assignment.recipientMode,
                        assignment.createdAt,
                        assignment.updatedAt,
                        assignment.title
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
            select new com.erg.ergspringboot.lms.persistence.projection.TeacherHomeworkSummaryProjection(
                count(distinct case when lower(assignment.status) in ('assigned', 'active', 'published') then assignment.id else null end),
                count(distinct case when lower(assignment.status) in ('assigned', 'active', 'published')
                    and assignment.dueAt is not null
                    and assignment.dueAt < CURRENT_TIMESTAMP
                    then assignment.id else null end),
                count(distinct case when exists (
                    select 1 from Attempt attempt
                    where attempt.tenantId = assignment.tenantId
                      and attempt.assignmentId = assignment.id
                      and lower(attempt.status) = 'submitted') then assignment.id else null end)
            )
            from Assignment assignment
            where assignment.tenantId = :tenantId
              and assignment.assignedBy = :assignedBy
            """)
    TeacherHomeworkSummaryProjection summarizeTeacherHomeworkWorkspace(String tenantId, String assignedBy);


    @Query(StudentDashboardAssignmentQuery.ASSIGNMENTS)
    List<StudentDashboardAssignmentProjection> findStudentDashboardAssignments(
            String tenantId,
            String academicClassId,
            String studentId,
            java.util.Collection<String> completedStates,
            Pageable pageable
    );

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default List<StudentDashboardAssignmentProjection> findStudentDashboardAssignments(
            String tenantId, String academicClassId, String studentId, Pageable pageable) {
        return findStudentDashboardAssignments(tenantId, academicClassId, studentId,
                com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates(), pageable);
    }

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentCountProjection(
                count(distinct case when lower(assignment.status) in ('assigned', 'active', 'published') then assignment.id else null end),
                count(distinct case when lower(assignment.status) in ('assigned', 'active', 'published')
                    and not exists (
                        select attempt.id
                        from Attempt attempt
                        where attempt.tenantId = assignment.tenantId
                          and attempt.assignmentId = assignment.id
                          and attempt.studentId = :studentId
                          and lower(attempt.status) in :completedStates
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
    StudentDashboardAssignmentCountProjection summarizeStudentDashboardAssignments(String tenantId,
            String academicClassId,
            String studentId, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default StudentDashboardAssignmentCountProjection summarizeStudentDashboardAssignments(String tenantId,
            String academicClassId,
            String studentId) {
        return summarizeStudentDashboardAssignments(tenantId, academicClassId, studentId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }

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
              and lower(assignment.status) in ('assigned', 'active', 'published')
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
              and lower(assignment.status) in ('assigned', 'active', 'published')
              and not exists (
                  select attempt.id
                  from Attempt attempt
                  where attempt.tenantId = assignment.tenantId
                    and attempt.assignmentId = assignment.id
                    and attempt.studentId = :studentId
                    and lower(attempt.status) in :completedStates
              )
            """)
    long countPendingStudentAssignmentActions(String tenantId, String academicClassId, String studentId, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default long countPendingStudentAssignmentActions(String tenantId, String academicClassId, String studentId) {
        return countPendingStudentAssignmentActions(tenantId, academicClassId, studentId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
}
