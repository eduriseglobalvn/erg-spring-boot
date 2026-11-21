package com.erg.ergspringboot.lms.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class ClassRiskSummaryRepositoryContractTest {

    @Test
    void riskCountersAreExistsSubqueriesNotJoinsThatMultiplyRows() throws Exception {
        Query query = ClassRiskSummaryRepository.class.getMethod("summarizeClassWorkspaceRisks",
                String.class, String.class, double.class, Collection.class).getAnnotation(Query.class);

        assertThat(query.value())
                .contains("ClassRiskSummaryProjection", "student.tenantId = :tenantId", "student.academicClassId = :academicClassId",
                        "low.percent < :lowScoreThreshold")
                .doesNotContain("join")
                .doesNotContain("distinct");
        assertThat(query.value().split("exists \\(", -1).length - 1).isGreaterThanOrEqualTo(3);
    }

    @Test
    void theRecipientProgressQueriesNeverJoinEveryStudentOfTheTenant() {
        assertThat(AssignmentRecipientProgressSql.FROM_ROWS).contains("recipient.assignmentId = :assignmentId")
                .contains("student.id = recipient.studentId");
        assertThat(AssignmentRecipientProgressSql.FROM_ROSTER).contains("student.academicClassId = assignment.academicClassId");
        assertThat(AssignmentRecipientProgressSql.FROM_STUDENTS).contains("student.id in :studentIds");
    }
}
