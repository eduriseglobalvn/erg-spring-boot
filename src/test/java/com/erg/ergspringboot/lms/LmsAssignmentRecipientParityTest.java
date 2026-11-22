package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipient;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** isRecipient must follow the student assignment SQL: recipient rows decide when present, else class / student_ids. */
class LmsAssignmentRecipientParityTest {
    private final LmsAttemptFixture fixture = new LmsAttemptFixture();

    private Assignment assignment(List<String> studentIds) {
        Assignment a = new Assignment();
        a.setId("asg-1");
        a.setTenantId("erg");
        a.setAcademicClassId("class-1");
        a.setStudentIds(studentIds);
        return a;
    }

    private Student student(String id, String classId) {
        Student s = new Student();
        s.setId(id);
        s.setAcademicClassId(classId);
        return s;
    }

    @Test
    void legacyAssignmentWithoutRowsAcceptsStudentIdsOrClassMembers() {
        Assignment legacy = assignment(List.of("stu-1"));
        // the snapshot is asked in SQL: it names stu-1 only
        when(fixture.recipients.legacySnapshotNames(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(call -> "stu-1".equals(call.getArgument(1)));
        assertThat(fixture.policy.isRecipient(legacy, student("stu-1", "other"), null)).isTrue();
        assertThat(fixture.policy.isRecipient(legacy, student("stu-2", "class-1"), null)).isTrue();
        assertThat(fixture.policy.isRecipient(legacy, student("stu-3", "other"), null)).isFalse();
        assertThat(fixture.policy.isRecipient(legacy, null, null)).isFalse();
    }

    @Test
    void recipientRowsDecideAloneOnceTheAssignmentHasAny() {
        Assignment a = assignment(List.of("stu-1"));
        when(fixture.recipients.existsByTenantIdAndAssignmentId("erg", "asg-1")).thenReturn(true);
        when(fixture.recipients.findFirstByTenantIdAndAssignmentIdAndStudentId("erg", "asg-1", "stu-2"))
                .thenReturn(Optional.of(new AssignmentRecipient()));
        assertThat(fixture.policy.isRecipient(a, student("stu-2", "other"), null)).isTrue();
        // listed in student_ids and in the class, but without a row: the SQL excludes them, so must Java.
        assertThat(fixture.policy.isRecipient(a, student("stu-1", "class-1"), null)).isFalse();
    }

    @Test
    void aStudentAddedLaterWithARowIsRecipientEvenWhenMissingFromStudentIds() {
        Assignment a = assignment(List.of("stu-1"));
        when(fixture.recipients.existsByTenantIdAndAssignmentId("erg", "asg-1")).thenReturn(true);
        when(fixture.recipients.findFirstByTenantIdAndAssignmentIdAndStudentId("erg", "asg-1", "stu-9"))
                .thenReturn(Optional.of(new AssignmentRecipient()));
        assertThat(fixture.policy.isRecipient(a, student("stu-9", "class-1"), "user-9")).isTrue();
    }

    /** The single exists query behind isAssignedQuiz must state the same rule as the list SQL and the visibility policy. */
    @Test
    void learnerQuizExistsQueryStatesTheSameRuleAsTheListSqlAndVisibilityPolicy() throws Exception {
        String exists = normalized(AssignmentRepository.class.getMethod("existsVisibleQuizForLearner",
                String.class, String.class, String.class, String.class, String.class, String.class));
        String list = normalized(AssignmentRepository.class.getMethod("findStudentAssignments",
                String.class, String.class, String.class, org.springframework.data.domain.Pageable.class));

        // Visibility: exactly the states LmsStatePolicy.assignmentIsVisible accepts.
        var visible = java.util.regex.Pattern.compile("lower\\(assignment\\.status\\) in \\(([^)]*)\\)").matcher(exists);
        assertThat(visible.find()).isTrue();
        List<String> states = java.util.Arrays.stream(visible.group(1).split(",")).map(s -> s.trim().replace("'", "")).toList();
        assertThat(states).containsExactlyInAnyOrder("assigned", "active", "published");
        states.forEach(state -> assertThat(LmsStatePolicy.assignmentIsVisible(state)).as(state).isTrue());
        assertThat(LmsStatePolicy.assignmentIsVisible("draft")).isFalse();
        assertThat(LmsStatePolicy.assignmentIsVisible("closed")).isFalse();

        // Recipient rule: a saved row for the student wins ...
        String row = "from assignment_recipients recipient where recipient.tenant_id = assignment.tenant_id"
                + " and recipient.assignment_id = assignment.id and recipient.student_id";
        assertThat(exists).contains(row);
        assertThat(list).contains(" join assignment_recipients recipient on recipient.tenant_id = assignment.tenant_id"
                + " and recipient.assignment_id = assignment.id and recipient.student_id = :studentId");
        // ... the class and the legacy student_ids snapshot only count while the assignment has no rows at all.
        String noRows = "not exists (select 1 from assignment_recipients r where r.tenant_id = assignment.tenant_id"
                + " and r.assignment_id = assignment.id)";
        assertThat(exists).contains(noRows + " and (assignment.academic_class_id = ");
        assertThat(exists).contains("assignment.student_ids @> jsonb_build_array(cast(:studentId as text))");
        assertThat(exists).contains("assignment.student_ids @> jsonb_build_array(cast(:authUserId as text))");
        assertThat(list).contains(noRows);
        assertThat(list).contains("assignment.academic_class_id = :academicClassId");
        assertThat(list).contains("assignment.student_ids @> jsonb_build_array(cast(:studentId as text))");
        // The quiz is the assignment's primary quiz or one of its assignment_resources rows.
        assertThat(exists).contains("assignment.quiz_id = :quizId").contains("from assignment_resources resource");
    }

    @Test
    void isAssignedQuizHandsTheLearnerTheSameWayIsRecipientReadsIt() {
        Student learner = student("stu-1", "class-1");
        when(fixture.assignments.existsVisibleQuizForLearner("erg", "quiz-1", null, "stu-1", "class-1", "user-1")).thenReturn(true);
        when(fixture.assignments.existsVisibleQuizForLearner("erg", "quiz-1", "asg-1", null, null, "user-9")).thenReturn(true);

        assertThat(fixture.policy.isAssignedQuiz("erg", "quiz-1", learner, "user-1")).isTrue();
        assertThat(fixture.policy.isAssignedQuiz("erg", "quiz-1", "asg-1", null, "user-9")).isTrue();
        assertThat(fixture.policy.isAssignedQuiz("erg", "quiz-1", null, "user-1")).isFalse();
    }

    private static String normalized(java.lang.reflect.Method method) {
        return method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value().replaceAll("\\s+", " ").trim();
    }

    @Test
    void recipientCountPrefersRowsAndFallsBackToTheSnapshot() {
        Assignment a = assignment(List.of("stu-1", "stu-2"));
        when(fixture.recipients.countLegacyStudentIds("asg-1")).thenReturn(2);
        assertThat(fixture.policy.recipientCount(a)).isEqualTo(2);
        when(fixture.recipients.countByAssignmentId("asg-1")).thenReturn(5L);
        assertThat(fixture.policy.recipientCount(a)).isEqualTo(5);
    }
}
