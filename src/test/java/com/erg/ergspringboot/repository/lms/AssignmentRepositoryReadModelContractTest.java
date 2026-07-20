package com.erg.ergspringboot.repository.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AssignmentRepositoryReadModelContractTest {

    @Test
    void assignmentProgressDetailUsesProjectionWithoutEntityOrTeacherNote() throws Exception {
        Method method = AssignmentRepository.class.getMethod(
                "findAssignmentDetail",
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("AssignmentDetailProjection")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.id = :assignmentId")
                .doesNotContain("assignment.teacherNote")
                .doesNotContain("assignment.studentIds")
                .doesNotContain("select assignment")
                .doesNotContain("join fetch");
    }

    @Test
    void teacherHomeworkWorkspaceUsesProjectionPageWithBoundedCountQuery() throws Exception {
        Method method = AssignmentRepository.class.getMethod(
                "findTeacherAssignmentCards",
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("AssignmentCardProjection");
        assertThat(query.value())
                .contains("AssignmentCardProjection")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.assignedBy = :assignedBy")
                .contains("(:status is null or lower(assignment.status) = :status)")
                .doesNotContain("select assignment")
                .doesNotContain("join fetch")
                .doesNotContain("assignment.attempts");
        assertThat(query.countQuery())
                .contains("count(assignment.id)")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.assignedBy = :assignedBy")
                .contains("(:status is null or lower(assignment.status) = :status)")
                .doesNotContain("AssignmentCardProjection")
                .doesNotContain("join fetch");
    }

    @Test
    void classWorkspaceAssignmentsUseProjectionPageWithBoundedCountQuery() throws Exception {
        Method method = AssignmentRepository.class.getMethod(
                "findClassAssignmentCards",
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("AssignmentCardProjection");
        assertThat(query.value())
                .contains("AssignmentCardProjection")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.academicClassId = :academicClassId")
                .contains("(:status is null or lower(assignment.status) = :status)")
                .doesNotContain("select assignment")
                .doesNotContain("join fetch")
                .doesNotContain("assignment.attempts");
        assertThat(query.countQuery())
                .contains("count(assignment.id)")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.academicClassId = :academicClassId")
                .contains("(:status is null or lower(assignment.status) = :status)")
                .doesNotContain("AssignmentCardProjection")
                .doesNotContain("join fetch");
    }

    @Test
    void teacherHomeworkSummaryUsesSingleAggregateProjectionQuery() throws Exception {
        Method method = AssignmentRepository.class.getMethod(
                "summarizeTeacherHomeworkWorkspace",
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("TeacherHomeworkSummaryProjection");
        assertThat(query.value())
                .contains("TeacherHomeworkSummaryProjection")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.assignedBy = :assignedBy")
                .contains("count(distinct case when lower(assignment.status) = 'active'")
                .contains("count(distinct case when lower(assignment.status) = 'active'")
                .contains("count(distinct case when lower(attempt.status) = 'submitted'")
                .doesNotContain("sum(case when lower(assignment.status) = 'active'")
                .doesNotContain("select assignment")
                .doesNotContain("join fetch");
    }

    @Test
    void studentDashboardAssignmentsUseFlattenedRecipientsWithLegacyFallback() throws Exception {
        Method assignments = AssignmentRepository.class.getMethod(
                "findStudentDashboardAssignments",
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Method activeCount = AssignmentRepository.class.getMethod(
                "countActiveStudentAssignments",
                String.class,
                String.class,
                String.class
        );
        Method pendingCount = AssignmentRepository.class.getMethod(
                "countPendingStudentAssignmentActions",
                String.class,
                String.class,
                String.class
        );

        assertStudentDashboardRecipientQuery(assignments.getAnnotation(Query.class).value());
        assertStudentDashboardRecipientQuery(activeCount.getAnnotation(Query.class).value());
        assertStudentDashboardRecipientQuery(pendingCount.getAnnotation(Query.class).value());
    }

    private void assertStudentDashboardRecipientQuery(String query) {
        assertThat(query)
                .contains("left join AssignmentRecipient recipient")
                .contains("recipient.assignmentId = assignment.id")
                .contains("recipient.studentId = :studentId")
                .contains("recipient.id is not null")
                .contains("not exists")
                .contains("from AssignmentRecipient anyRecipient")
                .contains("assignment.academicClassId = :academicClassId")
                .doesNotContain("where assignment.tenantId = :tenantId\n              and assignment.academicClassId = :academicClassId");
    }
}
