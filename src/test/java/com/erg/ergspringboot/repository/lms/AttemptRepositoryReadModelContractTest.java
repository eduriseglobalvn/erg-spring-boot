package com.erg.ergspringboot.repository.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AttemptRepositoryReadModelContractTest {

    @Test
    void assignmentProgressSummaryUsesAggregateProjectionInsteadOfAttemptEntities() throws Exception {
        Method method = AttemptRepository.class.getMethod(
                "summarizeAssignmentProgress",
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("AssignmentProgressProjection")
                .contains("sum(case when lower(attempt.status) = 'submitted'")
                .contains("sum(case when lower(attempt.status) = 'in_progress'")
                .contains("attempt.tenantId = :tenantId")
                .contains("attempt.assignmentId = :assignmentId")
                .doesNotContain("select attempt");
    }

    @Test
    void assignmentAttemptSummariesUsePageProjectionWithoutRawAttemptPayloads() throws Exception {
        Method method = AttemptRepository.class.getMethod(
                "findAssignmentAttemptSummaries",
                String.class,
                String.class,
                String.class,
                org.springframework.data.domain.Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("AttemptSummaryProjection")
                .contains("attempt.tenantId = :tenantId")
                .contains("attempt.assignmentId = :assignmentId")
                .contains("(:status is null or lower(attempt.status) = :status)")
                .contains("order by attempt.updatedAt desc")
                .doesNotContain("attempt.answers")
                .doesNotContain("attempt.events")
                .doesNotContain("attempt.client")
                .doesNotContain("attempt.packageHash")
                .doesNotContain("select attempt");
        assertThat(query.countQuery())
                .contains("count(attempt.id)")
                .contains("attempt.tenantId = :tenantId")
                .contains("attempt.assignmentId = :assignmentId");
    }

    @Test
    void classWorkspaceScoreSummaryUsesAggregateProjectionAndClassJoin() throws Exception {
        Method method = AttemptRepository.class.getMethod(
                "summarizeClassWorkspaceScores",
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("ClassScoreSummaryProjection")
                .contains("count(attempt.id)")
                .contains("count(distinct attempt.studentId)")
                .contains("avg(attempt.percent)")
                .contains("max(attempt.percent)")
                .contains("assignment.academicClassId = :academicClassId")
                .contains("attempt.tenantId = :tenantId")
                .doesNotContain("select attempt")
                .doesNotContain("join fetch");
    }

    @Test
    void teacherHomeworkPendingReviewsUsePageProjectionWithoutRawAttemptPayloads() throws Exception {
        Method method = AttemptRepository.class.getMethod(
                "findTeacherPendingReviews",
                String.class,
                String.class,
                org.springframework.data.domain.Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("PendingReviewProjection");
        assertThat(query.value())
                .contains("PendingReviewProjection")
                .contains("assignment.assignedBy = :teacherId")
                .contains("attempt.tenantId = :tenantId")
                .contains("lower(attempt.status) in ('submitted', 'needs_review', 'review_required')")
                .doesNotContain("attempt.answers")
                .doesNotContain("attempt.events")
                .doesNotContain("attempt.client")
                .doesNotContain("attempt.packageHash")
                .doesNotContain("select attempt")
                .doesNotContain("join fetch");
        assertThat(query.countQuery())
                .contains("count(attempt.id)")
                .contains("assignment.assignedBy = :teacherId")
                .doesNotContain("PendingReviewProjection")
                .doesNotContain("join fetch");
    }
}
