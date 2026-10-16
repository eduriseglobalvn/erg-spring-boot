package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassScoreSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardScoreSummaryProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, String> {

    Optional<Attempt> findByTenantIdAndId(String tenantId, String id);

    Optional<Attempt> findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStatusIgnoreCase(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            String status
    );

    Optional<Attempt> findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStartIdempotencyKey(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            String startIdempotencyKey
    );

    List<Attempt> findByTenantIdAndAssignmentIdOrderByUpdatedAtDesc(String tenantId, String assignmentId);

    List<Attempt> findByTenantIdAndQuizIdOrderByUpdatedAtDesc(String tenantId, String quizId);

    List<Attempt> findTop3ByTenantIdAndAssignmentIdAndStudentIdOrderByUpdatedAtDesc(
            String tenantId,
            String assignmentId,
            String studentId
    );

    List<Attempt> findByTenantIdAndAssignmentIdInAndStudentIdOrderByAssignmentIdAscUpdatedAtDesc(
            String tenantId,
            Collection<String> assignmentIds,
            String studentId
    );

    @Query("""
            select count(distinct attempt.id)
            from Attempt attempt
            left join Quiz quiz
                on quiz.tenantId = attempt.tenantId
                and quiz.id = attempt.quizId
            left join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and (
                  quiz.subjectId = :subjectId
                  or assignment.subjectId = :subjectId
              )
            """)
    long countImpactByTenantAndSubject(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);

    @Query("""
            select count(distinct attempt.id)
            from Attempt attempt
            join Quiz quiz
                on quiz.tenantId = attempt.tenantId
                and quiz.id = attempt.quizId
            where attempt.tenantId = :tenantId
              and quiz.levelId = :levelId
            """)
    long countImpactByTenantAndLevel(@Param("tenantId") String tenantId, @Param("levelId") String levelId);

    @Query(
            value = """
                    select count(distinct attempt.id)
                    from lms_attempts attempt
                    join lms_quizzes quiz
                      on quiz.tenant_id = attempt.tenant_id
                     and quiz.id = attempt.quiz_id
                    where attempt.tenant_id = :tenantId
                      and (
                          quiz.category_id = :topicId
                          or jsonb_exists(coalesce(quiz.topic_ids, '[]'::jsonb), :topicId)
                      )
                    """,
            nativeQuery = true
    )
    long countImpactByTenantAndTopic(@Param("tenantId") String tenantId, @Param("topicId") String topicId);

    @Query("""
            select count(attempt.id)
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
              and attempt.quizId = :quizId
              and attempt.studentId = :studentId
              and lower(attempt.status) in :statuses
            """)
    long countByStatusInForStudentAssignmentQuiz(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            Collection<String> statuses
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection(
                coalesce(sum(case when lower(attempt.status) = 'submitted' then 1 else 0 end), 0),
                coalesce(sum(case when lower(attempt.status) = 'in_progress' then 1 else 0 end), 0),
                coalesce(sum(case when lower(attempt.status) in ('needs_review', 'review_required') then 1 else 0 end), 0)
            )
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
            """)
    AssignmentProgressProjection summarizeAssignmentProgress(String tenantId, String assignmentId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection(
                        attempt.id,
                        attempt.assignmentId,
                        attempt.quizId,
                        attempt.studentId,
                        attempt.status,
                        attempt.score,
                        attempt.maxScore,
                        attempt.percent,
                        attempt.passed,
                        attempt.startedAt,
                        attempt.submittedAt,
                        attempt.updatedAt
                    )
                    from Attempt attempt
                    where attempt.tenantId = :tenantId
                      and attempt.assignmentId = :assignmentId
                      and (:status is null or lower(attempt.status) = :status)
                    order by attempt.updatedAt desc
                    """,
            countQuery = """
                    select count(attempt.id)
                    from Attempt attempt
                    where attempt.tenantId = :tenantId
                      and attempt.assignmentId = :assignmentId
                      and (:status is null or lower(attempt.status) = :status)
                    """
    )
    Page<AttemptSummaryProjection> findAssignmentAttemptSummaries(
            String tenantId,
            String assignmentId,
            String status,
            Pageable pageable
    );

    // Summary projection avoids deserializing the answers/events/client jsonb blobs (unused in the
    // students list view); Pageable caps a popular quiz's thousands of attempts.
    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection(
                attempt.id,
                attempt.assignmentId,
                attempt.quizId,
                attempt.studentId,
                attempt.status,
                attempt.score,
                attempt.maxScore,
                attempt.percent,
                attempt.passed,
                attempt.startedAt,
                attempt.submittedAt,
                attempt.updatedAt
            )
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.quizId = :quizId
            order by attempt.updatedAt desc
            """)
    List<AttemptSummaryProjection> findQuizAttemptSummaries(
            String tenantId,
            String quizId,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection(
                attempt.id,
                attempt.assignmentId,
                attempt.quizId,
                attempt.studentId,
                attempt.status,
                attempt.score,
                attempt.maxScore,
                attempt.percent,
                attempt.passed,
                attempt.startedAt,
                attempt.submittedAt,
                attempt.updatedAt
            )
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
              and attempt.studentId in :studentIds
              and not exists (
                  select newerAttempt.id
                  from Attempt newerAttempt
                  where newerAttempt.tenantId = attempt.tenantId
                    and newerAttempt.assignmentId = attempt.assignmentId
                    and newerAttempt.studentId = attempt.studentId
                    and (
                        newerAttempt.updatedAt > attempt.updatedAt
                        or (
                            newerAttempt.updatedAt = attempt.updatedAt
                            and newerAttempt.id > attempt.id
                        )
                    )
              )
            """)
    List<AttemptScoreProjection> findLatestAttemptScoresForRecipients(
            String tenantId,
            String assignmentId,
            Collection<String> studentIds
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection(
                attempt.id,
                attempt.assignmentId,
                attempt.quizId,
                attempt.studentId,
                attempt.status,
                attempt.score,
                attempt.maxScore,
                attempt.percent,
                attempt.passed,
                attempt.startedAt,
                attempt.submittedAt,
                attempt.updatedAt
            )
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
              and attempt.studentId in :studentIds
              and attempt.percent is not null
              and lower(attempt.status) in ('submitted', 'graded', 'completed')
              and not exists (
                  select betterAttempt.id
                  from Attempt betterAttempt
                  where betterAttempt.tenantId = attempt.tenantId
                    and betterAttempt.assignmentId = attempt.assignmentId
                    and betterAttempt.studentId = attempt.studentId
                    and betterAttempt.percent is not null
                    and lower(betterAttempt.status) in ('submitted', 'graded', 'completed')
                    and (
                        betterAttempt.percent > attempt.percent
                        or (
                            betterAttempt.percent = attempt.percent
                            and betterAttempt.updatedAt > attempt.updatedAt
                        )
                        or (
                            betterAttempt.percent = attempt.percent
                            and betterAttempt.updatedAt = attempt.updatedAt
                            and betterAttempt.id > attempt.id
                        )
                    )
              )
            """)
    List<AttemptScoreProjection> findBestAttemptScoresForRecipients(
            String tenantId,
            String assignmentId,
            Collection<String> studentIds
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection(
                        attempt.id,
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        student.id,
                        student.studentCode,
                        student.fullName,
                        attempt.status,
                        attempt.score,
                        attempt.maxScore,
                        attempt.percent,
                        attempt.submittedAt,
                        attempt.updatedAt
                    )
                    from Attempt attempt
                    join Assignment assignment
                        on assignment.tenantId = attempt.tenantId
                        and assignment.id = attempt.assignmentId
                    left join Student student
                        on student.tenantId = attempt.tenantId
                        and student.id = attempt.studentId
                    where attempt.tenantId = :tenantId
                      and assignment.assignedBy = :teacherId
                      and lower(attempt.status) in ('submitted', 'needs_review', 'review_required')
                    order by attempt.updatedAt desc
                    """,
            countQuery = """
                    select count(attempt.id)
                    from Attempt attempt
                    join Assignment assignment
                        on assignment.tenantId = attempt.tenantId
                        and assignment.id = attempt.assignmentId
                    where attempt.tenantId = :tenantId
                      and assignment.assignedBy = :teacherId
                      and lower(attempt.status) in ('submitted', 'needs_review', 'review_required')
                    """
    )
    Page<PendingReviewProjection> findTeacherPendingReviews(String tenantId, String teacherId, Pageable pageable);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection(
                        attempt.id,
                        attempt.assignmentId,
                        attempt.quizId,
                        attempt.studentId,
                        attempt.status,
                        attempt.score,
                        attempt.maxScore,
                        attempt.percent,
                        attempt.passed,
                        attempt.startedAt,
                        attempt.submittedAt,
                        attempt.updatedAt
                    )
                    from Attempt attempt
                    where attempt.tenantId = :tenantId
                      and attempt.studentId = :studentId
                    order by attempt.updatedAt desc
                    """,
            countQuery = """
                    select count(attempt.id)
                    from Attempt attempt
                    where attempt.tenantId = :tenantId
                      and attempt.studentId = :studentId
                    """
    )
    Page<AttemptSummaryProjection> findStudentAttemptSummaries(
            String tenantId,
            String studentId,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.ClassScoreSummaryProjection(
                count(attempt.id),
                count(distinct attempt.studentId),
                avg(attempt.percent),
                max(attempt.percent)
            )
            from Attempt attempt
            join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and assignment.academicClassId = :academicClassId
              and lower(attempt.status) in ('submitted', 'graded', 'completed')
            """)
    ClassScoreSummaryProjection summarizeClassWorkspaceScores(String tenantId, String academicClassId);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.StudentDashboardScoreSummaryProjection(
                count(attempt.id),
                count(distinct attempt.assignmentId),
                coalesce(sum(case when attempt.passed = true then 1 else 0 end), 0),
                avg(attempt.percent),
                max(attempt.percent)
            )
            from Attempt attempt
            left join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and attempt.studentId = :studentId
              and (
                  :academicClassId is null
                  or attempt.classId = :academicClassId
                  or (attempt.classId is null and assignment.academicClassId = :academicClassId)
              )
              and lower(attempt.status) in ('submitted', 'graded', 'completed')
            """)
    StudentDashboardScoreSummaryProjection summarizeStudentDashboardScores(
            String tenantId,
            String studentId,
            String academicClassId
    );
}
