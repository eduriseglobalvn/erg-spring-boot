package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.AttemptScoreProjection;
import com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassScoreSummaryProjection;
import com.erg.ergspringboot.lms.persistence.projection.PendingReviewProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardScoreSummaryProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, String>, AttemptProgressQueries {

    Optional<Attempt> findByTenantIdAndId(String tenantId, String id);

    /** Literal predicate so the planner can use the partial unique index uq_lms_attempts_single_in_progress (lower(status)). */
    @Query("select a from Attempt a where a.tenantId = :tenantId and a.assignmentId = :assignmentId and a.quizId = :quizId "
            + "and a.studentId = :studentId and lower(a.status) = 'in_progress'")
    Optional<Attempt> findInProgress(String tenantId, String assignmentId, String quizId, String studentId);

    Optional<Attempt> findFirstByTenantIdAndAssignmentIdAndQuizIdAndStudentIdAndStartIdempotencyKey(
            String tenantId,
            String assignmentId,
            String quizId,
            String studentId,
            String startIdempotencyKey
    );

    /** Newest handed-in attempt first; pass {@code PageRequest.of(0, 1)} for the latest one. */
    @Query("""
            select attempt from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
              and attempt.studentId = :studentId
              and lower(attempt.status) in :statuses
            order by attempt.submittedAt desc nulls last, attempt.updatedAt desc
            """)
    List<Attempt> findLatestByStatuses(@Param("tenantId") String tenantId, @Param("assignmentId") String assignmentId,
            @Param("studentId") String studentId, @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    /** Newest three attempts per assignment as summaries (no answers jsonb); ties on updated_at break by id. */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection(a.id, a.assignmentId, a.quizId,
                a.studentId, a.status, a.score, a.maxScore, a.percent, a.passed, a.startedAt, a.submittedAt, a.updatedAt)
            from Attempt a
            where a.tenantId = :tenantId and a.assignmentId in :assignmentIds and a.studentId = :studentId
              and (select count(n.id) from Attempt n where n.tenantId = a.tenantId and n.assignmentId = a.assignmentId
                   and n.studentId = a.studentId and (n.updatedAt > a.updatedAt or (n.updatedAt = a.updatedAt and n.id > a.id))) < 3
            order by a.assignmentId, a.updatedAt desc, a.id desc
            """)
    List<AttemptSummaryProjection> findTop3SummariesByAssignmentsAndStudent(
            String tenantId, Collection<String> assignmentIds, String studentId);

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

    @Query(
            value = """
                    select new com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection(
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
            select new com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection(
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

    /**
     * Paged roster with the caller's class/education-unit scope applied in SQL, so a page is never cut
     * before filtering. Mirrors the in-memory rule: an assignment without a class is visible to every reviewer.
     */
    @Query(value = """
            select new com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection(
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
            join Assignment assignment
              on assignment.id = attempt.assignmentId and assignment.tenantId = attempt.tenantId
            left join Classroom classroom
              on classroom.id = assignment.academicClassId and classroom.tenantId = attempt.tenantId
            where attempt.tenantId = :tenantId
              and attempt.quizId = :quizId
              and (coalesce(assignment.academicClassId, '') = ''
                   or (classroom.id is not null
                       and (:allClasses = true or assignment.academicClassId in :classIds)
                       and (:allUnits = true or coalesce(classroom.schoolId, '') = ''
                            or classroom.schoolId in :unitIds)))
            order by attempt.updatedAt desc, attempt.id desc
            """, countQuery = """
            select count(attempt.id)
            from Attempt attempt
            join Assignment assignment
              on assignment.id = attempt.assignmentId and assignment.tenantId = attempt.tenantId
            left join Classroom classroom
              on classroom.id = assignment.academicClassId and classroom.tenantId = attempt.tenantId
            where attempt.tenantId = :tenantId
              and attempt.quizId = :quizId
              and (coalesce(assignment.academicClassId, '') = ''
                   or (classroom.id is not null
                       and (:allClasses = true or assignment.academicClassId in :classIds)
                       and (:allUnits = true or coalesce(classroom.schoolId, '') = ''
                            or classroom.schoolId in :unitIds)))
            """)
    Page<AttemptSummaryProjection> findScopedQuizAttemptSummaries(
            @Param("tenantId") String tenantId,
            @Param("quizId") String quizId,
            @Param("allClasses") boolean allClasses,
            @Param("classIds") Collection<String> classIds,
            @Param("allUnits") boolean allUnits,
            @Param("unitIds") Collection<String> unitIds,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AttemptScoreProjection(
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
            select new com.erg.ergspringboot.lms.persistence.projection.AttemptScoreProjection(
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
              and lower(attempt.status) in :completedStates
              and not exists (
                  select betterAttempt.id
                  from Attempt betterAttempt
                  where betterAttempt.tenantId = attempt.tenantId
                    and betterAttempt.assignmentId = attempt.assignmentId
                    and betterAttempt.studentId = attempt.studentId
                    and betterAttempt.percent is not null
                    and lower(betterAttempt.status) in :completedStates
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
    List<AttemptScoreProjection> findBestAttemptScoresForRecipients(String tenantId,
            String assignmentId,
            Collection<String> studentIds, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default List<AttemptScoreProjection> findBestAttemptScoresForRecipients(String tenantId,
            String assignmentId,
            Collection<String> studentIds) {
        return findBestAttemptScoresForRecipients(tenantId, assignmentId, studentIds, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }

    @Query(
            value = """
                    select new com.erg.ergspringboot.lms.persistence.projection.PendingReviewProjection(
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
                      and lower(attempt.status) in :reviewStates
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
                      and lower(attempt.status) in :reviewStates
                    """
    )
    Page<PendingReviewProjection> findTeacherPendingReviews(String tenantId, String teacherId, Pageable pageable, java.util.Collection<String> reviewStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default Page<PendingReviewProjection> findTeacherPendingReviews(String tenantId, String teacherId, Pageable pageable) {
        return findTeacherPendingReviews(tenantId, teacherId, pageable, com.erg.ergspringboot.lms.LmsStatePolicy.reviewQueueAttemptStates());
    }

    @Query(
            value = """
                    select new com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection(
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
            select new com.erg.ergspringboot.lms.persistence.projection.ClassScoreSummaryProjection(
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
              and lower(attempt.status) in :completedStates
            """ + LatestHandedInAttempt.OF_ATTEMPT)
    ClassScoreSummaryProjection summarizeClassWorkspaceScores(String tenantId, String academicClassId, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default ClassScoreSummaryProjection summarizeClassWorkspaceScores(String tenantId, String academicClassId) {
        return summarizeClassWorkspaceScores(tenantId, academicClassId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.StudentDashboardScoreSummaryProjection(
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
              and lower(attempt.status) in :completedStates
            """ + LatestHandedInAttempt.OF_ATTEMPT)
    StudentDashboardScoreSummaryProjection summarizeStudentDashboardScores(String tenantId,
            String studentId,
            String academicClassId, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default StudentDashboardScoreSummaryProjection summarizeStudentDashboardScores(String tenantId,
            String studentId,
            String academicClassId) {
        return summarizeStudentDashboardScores(tenantId, studentId, academicClassId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
}
