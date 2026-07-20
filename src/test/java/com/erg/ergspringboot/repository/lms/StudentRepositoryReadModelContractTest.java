package com.erg.ergspringboot.repository.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class StudentRepositoryReadModelContractTest {

    @Test
    void classRosterUsesProjectionWithTenantClassAndStatusBounds() throws Exception {
        Method method = StudentRepository.class.getMethod(
                "findClassRoster",
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("StudentRosterProjection");
        assertThat(query.value())
                .contains("StudentRosterProjection")
                .contains("student.tenantId = :tenantId")
                .contains("student.academicClassId = :academicClassId")
                .contains("lower(student.status) = :status")
                .contains("order by student.fullName asc, student.studentCode asc")
                .doesNotContain("select student")
                .doesNotContain("join fetch")
                .doesNotContain("student.attempts")
                .doesNotContain("student.assignments");
        assertThat(query.countQuery())
                .contains("count(student.id)")
                .contains("student.tenantId = :tenantId")
                .contains("student.academicClassId = :academicClassId");
    }

    @Test
    void classRiskSummaryUsesAggregateProjectionWithTenantClassBounds() throws Exception {
        Method method = StudentRepository.class.getMethod(
                "summarizeClassWorkspaceRisks",
                String.class,
                String.class,
                double.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("ClassRiskSummaryProjection")
                .contains("count(distinct case when overdueAssignment.id is not null")
                .contains("count(distinct case when lowScoreAttempt.id is not null")
                .contains("student.tenantId = :tenantId")
                .contains("student.academicClassId = :academicClassId")
                .contains("lowScoreAttempt.percent < :lowScoreThreshold")
                .doesNotContain("select student")
                .doesNotContain("join fetch")
                .doesNotContain("answers")
                .doesNotContain("events")
                .doesNotContain("client");
    }

    @Test
    void teacherHomeworkOverdueStudentsUsePageProjectionAndAntiJoin() throws Exception {
        Method method = StudentRepository.class.getMethod(
                "findTeacherOverdueStudents",
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("OverdueStudentProjection");
        assertThat(query.value())
                .contains("OverdueStudentProjection")
                .contains("assignment.assignedBy = :teacherId")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.dueAt < CURRENT_TIMESTAMP")
                .contains("completedAttempt.id is null")
                .contains("upper(assignment.recipientMode) = 'CLASS'")
                .doesNotContain("select student")
                .doesNotContain("select assignment")
                .doesNotContain("join fetch")
                .doesNotContain("answers")
                .doesNotContain("events")
                .doesNotContain("client")
                .doesNotContain("teacherNote");
        assertThat(query.countQuery())
                .contains("count(student.id)")
                .contains("assignment.assignedBy = :teacherId")
                .contains("completedAttempt.id is null")
                .doesNotContain("OverdueStudentProjection")
                .doesNotContain("join fetch");
    }
}
