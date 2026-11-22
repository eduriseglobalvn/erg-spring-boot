package com.erg.ergspringboot.lms.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** The SQL shapes the V175/V176 indexes and the V115/V164 unique indexes need; changing them silently re-introduces scans. */
class LmsIndexedQueryContractTest {

    private static String sql(Class<?> repository, String method) {
        Method found = Arrays.stream(repository.getMethods()).filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
        return found.getAnnotation(Query.class).value();
    }

    @Test
    void startAttemptLookupUsesTheLiteralPredicateOfThePartialUniqueIndex() {
        assertThat(sql(AttemptRepository.class, "findInProgress")).contains("lower(a.status) = 'in_progress'").doesNotContain("upper(");
    }

    @Test
    void studentCodeLookupsMatchTheLowerCaseIndexExpression() {
        assertThat(sql(StudentRepository.class, "findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase"))
                .contains("lower(student.studentCode) = lower(:studentCode)").doesNotContain("upper(");
        assertThat(sql(StudentRepository.class, "findAudienceStudentsByLowerKeys")).contains("lower(student.studentCode) in :codes");
    }

    @Test
    void studentAssignmentListSplitsTheOrSoBothIndexesApply() {
        String branches = AssignmentRecipientScope.STUDENT_ASSIGNMENTS_SQL;
        assertThat(branches).doesNotContain("academic_class_id = :academicClassId\n                      or")
                .contains("student_ids @> jsonb_build_array(cast(:studentId as text))")
                .contains("academic_class_id is distinct from");
        assertThat(branches.split("union all", -1)).hasSize(3);
    }

    @Test
    void studentAssignmentCountsUseTheSameOpenStatusesAsAssignmentIsVisible() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/erg/ergspringboot/lms/persistence/AssignmentRepository.java"));
        assertThat(source).doesNotContain("lower(assignment.status) = 'active'")
                .contains("lower(assignment.status) in ('assigned', 'active', 'published')");
    }

    @Test
    void top3AttemptsAreSelectedAsSummariesWithoutAnswers() {
        assertThat(sql(AttemptRepository.class, "findTop3SummariesByAssignmentsAndStudent"))
                .contains("AttemptSummaryProjection").contains(") < 3").doesNotContain("answers");
    }
}
