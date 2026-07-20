package com.erg.ergspringboot.repository.quiz.runtime;

import com.erg.ergspringboot.model.quiz.runtime.StudentProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, String> {

    Optional<StudentProgress> findByTenantIdAndStudentUserIdAndQuizIdAndAssignmentId(
            String tenantId,
            String studentUserId,
            String quizId,
            String assignmentId
    );

    List<StudentProgress> findByQuizIdAndStatus(String quizId, String status);

    List<StudentProgress> findByTenantIdAndAssignmentIdOrderBySubmittedAtDesc(String tenantId, String assignmentId);

    Page<StudentProgress> findByTenantIdAndAssignmentIdOrderBySubmittedAtDesc(
            String tenantId, String assignmentId, Pageable pageable);

    List<StudentProgress> findByTenantIdAndClassIdAndQuizIdOrderByStudentNameAsc(String tenantId, String classId, String quizId);

    @Query("""
            select
                count(progress.id) as totalRecipients,
                coalesce(sum(case when lower(progress.status) = 'submitted' then 1 else 0 end), 0) as submittedCount,
                coalesce(sum(case when lower(progress.status) = 'in_progress' then 1 else 0 end), 0) as inProgressCount,
                coalesce(sum(case when lower(progress.status) in ('needs_review', 'review_required') then 1 else 0 end), 0) as needsReviewCount,
                coalesce(avg(progress.score), 0) as averagePercent
            from StudentProgress progress
            where progress.tenantId = :tenantId
              and progress.assignmentId = :assignmentId
            """)
    StudentProgressSummaryProjection summarizeAssignment(String tenantId, String assignmentId);

    @Query(value = """
            select
                coalesce(sum(total_recipients), 0) as totalRecipients,
                coalesce(sum(submitted_count), 0) as submittedCount,
                coalesce(sum(in_progress_count), 0) as inProgressCount,
                coalesce(sum(needs_review_count), 0) as needsReviewCount,
                case when coalesce(sum(scored_count), 0) = 0 then 0
                     else coalesce(sum(score_sum), 0) / sum(scored_count) end as averagePercent,
                coalesce(sum(source_version), 0) as sourceVersion
            from assignment_progress_aggregates
            where tenant_id = :tenantId and assignment_id = :assignmentId
            """, nativeQuery = true)
    AssignmentAggregateProjection summarizeAssignmentAggregate(String tenantId, String assignmentId);

    interface StudentProgressSummaryProjection {
        long getTotalRecipients();
        long getSubmittedCount();
        long getInProgressCount();
        long getNeedsReviewCount();
        double getAveragePercent();
    }

    interface AssignmentAggregateProjection extends StudentProgressSummaryProjection {
        long getSourceVersion();
    }
}
