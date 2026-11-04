package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ManualGradeRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentResource;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService.GradedAttempt;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Regression tests for B1/B2/B3/(b1)/(b2)/(d1)/N1/N2/N6/SQ-04/SQ-05 on the attempt command boundary. */
class LmsAttemptOwnershipAndGradingTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-08T09:00:00Z");

    private LmsAttemptFixture fixture;
    private LmsAttemptUseCase useCase;

    @BeforeEach
    void setUp() {
        fixture = new LmsAttemptFixture();
        useCase = fixture.useCase(Clock.fixed(Instant.parse("2026-08-08T09:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void learnerWhoseAuthIdDiffersFromStudentIdCanStartAndAttemptIsStoredUnderStudentId() {
        Assignment assignment = assignment();
        assignment.setStudentIds(List.of("stu-1"));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-1")).thenReturn(Optional.of(student("stu-1", "usr-1", "class-a")));

        var response = useCase.start("usr-1", start("quiz-1"), null, learner("usr-1"));

        assertThat(response.studentId()).isEqualTo("stu-1");
    }

    @Test
    void legacyAssignmentWithoutRecipientsDoesNotFailOpenForUnlinkedUsers() {
        Assignment assignment = assignment();
        assignment.setStudentIds(null);
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> useCase.start("usr-teacher", start("quiz-1"), null, learner("usr-teacher")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not assigned");
    }

    @Test
    void multiClassRecipientIsAuthorisedByRecipientNotByPrimaryClassScope() {
        Assignment assignment = assignment();
        assignment.setAcademicClassId("class-a");
        assignment.setStudentIds(List.of("stu-2"));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-2")).thenReturn(Optional.of(student("stu-2", "usr-2", "class-b")));
        UserAccessContext classB = new UserAccessContext("usr-2", "erg", List.of("school-1"), List.of("class-b"), null, null);

        assertThatCode(() -> useCase.start("usr-2", start("quiz-1"), null, classB)).doesNotThrowAnyException();
    }

    @Test
    void startAcceptsSecondaryQuizFromAssignmentResourcesAndUsesPinnedVersionPackage() {
        Assignment assignment = assignment();
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        AssignmentResource resource = new AssignmentResource();
        resource.setAssignmentId("asg-1");
        resource.setQuizId("quiz-2");
        resource.setQuizVersionId("ver-1");
        when(fixture.resources.findByTenantIdAndAssignmentIdAndQuizId("erg", "asg-1", "quiz-2")).thenReturn(Optional.of(resource));
        QuizPackage pinned = new QuizPackage();
        pinned.setId("pkg-v1");
        pinned.setQuizVersionId("ver-1");
        pinned.setContentHash("hash-v1");
        pinned.setGradingMode("server-authoritative");
        when(fixture.packages.packageEntityForVersion("erg", "quiz-2", "ver-1")).thenReturn(Optional.of(pinned));

        var response = useCase.start("usr-1", new StartAttemptRequest("asg-1", "quiz-2", "pkg-v1", "hash-v1"), null, learner("usr-1"));

        assertThat(response.quizId()).isEqualTo("quiz-2");
        assertThat(response.policySnapshot().quizVersionId()).isEqualTo("ver-1");
        verify(fixture.packages, never()).activePackageEntity(any(), any());
    }

    @Test
    void startRejectsQuizOutsideTheAssignment() {
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));

        assertThatThrownBy(() -> useCase.start("usr-1", start("quiz-9"), null, learner("usr-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not include quiz");
    }

    @Test
    void startRejectsDraftOrArchivedAssignments() {
        Assignment assignment = assignment();
        assignment.setStatus("draft");
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> useCase.start("usr-1", start("quiz-1"), null, learner("usr-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not open");
    }

    @Test
    void racingStartThatHitsTheSingleInProgressIndexReturnsConflict() {
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        when(fixture.attempts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_lms_attempts_single_in_progress"));

        assertThatThrownBy(() -> useCase.start("usr-1", start("quiz-1"), null, learner("usr-1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void startAutoClosesExpiredInProgressAttemptAndOpensANewOne() {
        Assignment assignment = assignment();
        assignment.setMaxDurationMinutes(10);
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        Attempt expired = attempt("usr-1");
        expired.setStartedAt(NOW.minusMinutes(30));
        when(fixture.attempts.findInProgress("erg", "asg-1", "quiz-1", "usr-1")).thenReturn(Optional.of(expired));

        var response = useCase.start("usr-1", start("quiz-1"), null, learner("usr-1"));

        assertThat(expired.getStatus()).isEqualTo("needs_review");
        assertThat(expired.isLate()).isTrue();
        assertThat(response.id()).isNotEqualTo("att-1");
        assertThat(response.status()).isEqualTo("in_progress");
    }

    @Test
    void anotherLearnerCannotSubmitSomeoneElsesAttemptEvenWithoutClass() {
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt("stu-a")));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-b")).thenReturn(Optional.of(student("stu-b", "usr-b", null)));

        assertThatThrownBy(() -> useCase.submit("att-1", submit(Map.of()), learner("usr-b")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> useCase.sync("att-1", null, learner("usr-b")))
                .isInstanceOf(ForbiddenException.class);
        verify(fixture.attempts, never()).save(any());
    }

    @Test
    void ownerResolvedThroughStudentLinkCanSubmitAndReviewerNeedsClassScope() {
        Attempt attempt = attempt("stu-a");
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        Assignment assignment = assignment();
        assignment.setAcademicClassId("class-a");
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));
        Classroom classroom = new Classroom();
        classroom.setId("class-a");
        classroom.setSchoolId("school-1");
        when(fixture.classrooms.findByTenantIdAndId("erg", "class-a")).thenReturn(Optional.of(classroom));
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "usr-a")).thenReturn(Optional.of(student("stu-a", "usr-a", "class-a")));

        assertThatCode(() -> useCase.sync("att-1", null, new UserAccessContext("usr-a", "erg", List.of(), List.of(), null, null)))
                .doesNotThrowAnyException();

        when(fixture.permissions.hasAny(LmsAssignmentAccessPolicy.REVIEW_PERMISSIONS)).thenReturn(true);
        UserAccessContext otherClassTeacher = new UserAccessContext("usr-t", "erg", List.of("school-1"), List.of("class-z"), null, null);
        assertThatThrownBy(() -> useCase.sync("att-1", null, otherClassTeacher)).isInstanceOf(ForbiddenException.class);
        UserAccessContext classTeacher = new UserAccessContext("usr-t", "erg", List.of("school-1"), List.of("class-a"), null, null);
        assertThatCode(() -> useCase.sync("att-1", null, classTeacher)).doesNotThrowAnyException();
    }

    @Test
    void submitInsideGraceWindowIsAcceptedAndMarkedLate() {
        Assignment assignment = assignment();
        assignment.setMaxDurationMinutes(10);
        Attempt attempt = attempt("usr-1");
        attempt.setStartedAt(NOW.minusMinutes(10).minusSeconds(30));
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment));

        useCase.submit("att-1", submit(Map.of()), learner("usr-1"));

        assertThat(attempt.isLate()).isTrue();
        assertThat(attempt.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    void ungradableSubmissionWaitsForReviewWithZeroScoreAndDropsUnknownAnswerKeys() {
        Attempt attempt = attempt("usr-1");
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setQuestionIds(List.of("q1", "q2"));
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz));
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("q1", "A");
        for (int i = 0; i < 50; i++) answers.put("junk-" + i, "x");

        var result = useCase.submit("att-1", submit(answers), learner("usr-1"));

        assertThat(result.score()).isZero();
        assertThat(result.passed()).isFalse();
        assertThat(attempt.getStatus()).isEqualTo("needs_review");
        assertThat(attempt.getAnswers()).containsOnlyKeys("q1");
        assertThat(attempt.getSubmittedCount()).isEqualTo(1);
    }

    @Test
    void versionedSubmissionGradesOnlyRealQuestionIds() {
        Attempt attempt = attempt("usr-1");
        attempt.setQuizVersionId("ver-1");
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        when(fixture.grading.runtimeQuestionIds("ver-1")).thenReturn(Set.of("q1"));
        when(fixture.grading.gradeSubmission(eq("erg"), eq("att-1"), eq("ver-1"), any()))
                .thenReturn(Optional.of(new GradedAttempt(BigDecimal.ONE, BigDecimal.ONE, 100, true, false, 1, 1)));

        useCase.submit("att-1", submit(Map.of("q1", "A", "junk", "B")), learner("usr-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> graded = ArgumentCaptor.forClass(Map.class);
        verify(fixture.grading).gradeSubmission(eq("erg"), eq("att-1"), eq("ver-1"), graded.capture());
        assertThat(graded.getValue()).containsOnlyKeys("q1");
        assertThat(attempt.getStatus()).isEqualTo("submitted");
    }

    @Test
    void projectionRunsAfterCommitAndItsFailureCannotBreakTheSubmission() {
        Attempt attempt = attempt("usr-1");
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        doThrow(new IllegalStateException("student_progress constraint")).when(fixture.grading).projectProgress(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean(), any(), any());
        TransactionSynchronizationManager.initSynchronization();

        assertThatCode(() -> useCase.submit("att-1", submit(Map.of()), learner("usr-1"))).doesNotThrowAnyException();
        verify(fixture.grading, never()).projectProgress(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), anyBoolean(), any(), any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(fixture.grading).projectProgress(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), anyBoolean(), any(), any());
    }

    @Test
    void teacherGradesManualAnswerAndAttemptMovesToGraded() {
        Attempt attempt = attempt("stu-a");
        attempt.setStatus("needs_review");
        attempt.setQuizVersionId("ver-1");
        attempt.setRecordVersion(3L);
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        when(fixture.permissions.hasAny(LmsAssignmentAccessPolicy.REVIEW_PERMISSIONS)).thenReturn(true);
        when(fixture.grading.gradeManualAnswer("erg", "att-1", "ver-1", "essay-1", BigDecimal.valueOf(2.0), "usr-t"))
                .thenReturn(new GradedAttempt(BigDecimal.TEN, BigDecimal.TEN, 100, true, false, 9, 9));
        UserAccessContext teacher = new UserAccessContext("usr-t", "erg", List.of("*"), List.of("*"), null, null);

        var response = useCase.gradeManualAnswer("att-1", "essay-1", new ManualGradeRequest(2.0, 3L), teacher);

        assertThat(response.status()).isEqualTo("graded");
        assertThat(response.percent()).isEqualTo(100.0);
        assertThat(response.passed()).isTrue();
    }

    @Test
    void gradingRequiresPermissionAndRejectsStaleVersion() {
        Attempt attempt = attempt("stu-a");
        attempt.setStatus("needs_review");
        attempt.setQuizVersionId("ver-1");
        attempt.setRecordVersion(4L);
        when(fixture.attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "asg-1")).thenReturn(Optional.of(assignment()));
        UserAccessContext teacher = new UserAccessContext("usr-t", "erg", List.of("*"), List.of("*"), null, null);

        assertThatThrownBy(() -> useCase.gradeManualAnswer("att-1", "essay-1", new ManualGradeRequest(1.0, 4L), teacher))
                .isInstanceOf(ForbiddenException.class);
        when(fixture.permissions.hasAny(LmsAssignmentAccessPolicy.REVIEW_PERMISSIONS)).thenReturn(true);
        assertThatThrownBy(() -> useCase.gradeManualAnswer("att-1", "essay-1", new ManualGradeRequest(1.0, 3L), teacher))
                .isInstanceOf(ConflictException.class);
    }

    private Assignment assignment() {
        Assignment assignment = new Assignment();
        assignment.setId("asg-1");
        assignment.setTenantId("erg");
        assignment.setAcademicClassId("");
        assignment.setQuizId("quiz-1");
        assignment.setStatus("active");
        assignment.setStudentIds(List.of("usr-1"));
        return assignment;
    }

    private Attempt attempt(String studentId) {
        Attempt attempt = new Attempt();
        attempt.setId("att-1");
        attempt.setTenantId("erg");
        attempt.setAssignmentId("asg-1");
        attempt.setQuizId("quiz-1");
        attempt.setStudentId(studentId);
        attempt.setPackageHash("hash-1");
        attempt.setStatus("in_progress");
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setStartedAt(NOW);
        return attempt;
    }

    private Student student(String id, String authUserId, String classId) {
        Student student = new Student();
        student.setId(id);
        student.setTenantId("erg");
        student.setAuthUserId(authUserId);
        student.setAcademicClassId(classId);
        return student;
    }

    private StartAttemptRequest start(String quizId) {
        return new StartAttemptRequest("asg-1", quizId, "pkg-1", "hash-1");
    }

    private SubmitAttemptRequest submit(Map<String, Object> answers) {
        return new SubmitAttemptRequest(answers, null, null, null, Map.of(), List.of());
    }

    private UserAccessContext learner(String userId) {
        return new UserAccessContext(userId, "erg", List.of(), List.of(), null, null);
    }
}
