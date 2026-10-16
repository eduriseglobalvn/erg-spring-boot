package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import com.erg.ergspringboot.service.lms.LmsAttemptService;
import com.erg.ergspringboot.service.lms.LmsClassroomService;
import com.erg.ergspringboot.service.lms.LmsQuizService;
import com.erg.ergspringboot.service.lms.LmsStudentService;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.service.quiz.runtime.AttemptEventRecorder;
import com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class LmsAttemptPolicyContractTest {

    private static final OffsetDateTime SERVER_NOW = OffsetDateTime.parse("2026-08-08T09:00:00Z");

    @Mock private LmsClassroomService classrooms;
    @Mock private LmsAssignmentService assignments;
    @Mock private LmsQuizService quizzes;
    @Mock private LmsAttemptService attempts;
    @Mock private LmsStudentService students;
    @Mock private QuizRuntimePackageService packages;
    @Mock private QuizAttemptGradingService grading;
    @Mock private AttemptEventRecorder events;
    @Mock private CacheManager caches;

    private LmsAttemptUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LmsAttemptUseCase(
                classrooms, assignments, quizzes, attempts, students, packages, grading, events, caches,
                Clock.fixed(Instant.parse("2026-08-08T09:00:00Z"), ZoneOffset.UTC));
        lenient().when(attempts.save(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attempts.findInProgress(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(attempts.countCompletedForLimit(any(), any(), any(), any())).thenReturn(0L);
    }

    @Test
    void startDerivesPackageAndPolicyFromServerAndReturnsSnapshot() {
        Assignment assignment = assignment();
        assignment.setAssignedBy("teacher-1");
        assignment.setAttemptLimit(2);
        assignment.setMaxDurationMinutes(45);
        assignment.setDueAt(SERVER_NOW.plusHours(2));
        assignment.setOfflineAllowed(true);
        Quiz quiz = quiz();
        QuizPackage pkg = packageEntity();
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz));
        when(packages.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.of(pkg));

        AttemptResponse response = useCase.start(
                "student-1", new StartAttemptRequest("assignment-1", "quiz-1", "pkg-server", "hash-server"),
                null, access());

        assertThat(response.packageId()).isEqualTo("pkg-server");
        assertThat(response.packageHash()).isEqualTo("hash-server");
        assertThat(response.policySnapshot()).isNotNull();
        assertThat(response.policySnapshot().teacherId()).isEqualTo("teacher-1");
        assertThat(response.policySnapshot().questionCount()).isEqualTo(2);
        assertThat(response.policySnapshot().durationMinutes()).isEqualTo(45);
        assertThat(response.policySnapshot().deadlineAt()).isEqualTo(SERVER_NOW.plusMinutes(45));
        assertThat(response.policySnapshot().attemptLimit()).isEqualTo(2);
        assertThat(response.policySnapshot().quizVersionId()).isEqualTo("version-1");
        assertThat(response.policySnapshot().quizVersion()).isEqualTo("7");
        assertThat(response.policySnapshot().offlineAllowed()).isTrue();
    }

    @Test
    void offlinePolicyDefaultsToDenyAndRejectsClientFirstPackages() {
        Assignment assignment = assignment();
        assignment.setOfflineAllowed(true);
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz()));
        QuizPackage clientFirst = packageEntity();
        clientFirst.setGradingMode("client-first");
        when(packages.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.of(clientFirst));

        AttemptResponse response = useCase.start(
                "student-1", new StartAttemptRequest("assignment-1", "quiz-1", "pkg-server", "hash-server"),
                null, access());

        assertThat(response.policySnapshot().offlineAllowed()).isFalse();

        Assignment defaultDeny = assignment();
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(defaultDeny));
        clientFirst.setGradingMode("server-authoritative");
        when(packages.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.of(clientFirst));
        AttemptResponse denied = useCase.start(
                "student-1", new StartAttemptRequest("assignment-1", "quiz-1", "pkg-server", "hash-server"),
                null, access());
        assertThat(denied.policySnapshot().offlineAllowed()).isFalse();
    }

    @Test
    void startRejectsClientPackageHashWhenItDoesNotMatchPublishedPackage() {
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment()));
        when(packages.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.of(packageEntity()));

        assertThatThrownBy(() -> useCase.start(
                "student-1", new StartAttemptRequest("assignment-1", "quiz-1", "pkg-server", "hash-client"),
                null, access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("package contract mismatch");

        verify(attempts, never()).save(any());
    }

    @Test
    void submitUsesServerTimeInsteadOfClientSubmittedAtForExpiry() {
        Assignment assignment = assignment();
        assignment.setMaxDurationMinutes(10);
        Attempt attempt = attempt();
        attempt.setStartedAt(SERVER_NOW.minusMinutes(11));
        when(attempts.findByTenantIdAndId("erg", "attempt-1")).thenReturn(Optional.of(attempt));
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> useCase.submit(
                "attempt-1",
                new SubmitAttemptRequest(Map.of(), SERVER_NOW.minusHours(4), "hash-1", "1", Map.of(), List.of()),
                access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duration exceeded");

        verify(attempts, never()).save(any());
    }

    @Test
    void submitWithSameIdempotencyKeyReplaysPersistedResultWithoutRegrading() {
        Attempt attempt = attempt();
        attempt.setStatus("submitted");
        attempt.setScore(8d);
        attempt.setMaxScore(10d);
        attempt.setPercent(80d);
        attempt.setPassed(true);
        attempt.setSubmitIdempotencyKey("submit-1");
        when(assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment()));
        when(attempts.findByTenantIdAndId("erg", "attempt-1")).thenReturn(Optional.of(attempt));

        var result = useCase.submit(
                "attempt-1",
                new SubmitAttemptRequest(Map.of(), null, "hash-1", "1", Map.of(), List.of(), "submit-1"),
                access());

        assertThat(result.score()).isEqualTo(8d);
        assertThat(result.maxScore()).isEqualTo(10d);
        assertThat(result.percent()).isEqualTo(80d);
        assertThat(result.passed()).isTrue();
        verify(grading, never()).gradeSubmission(any(), any(), any(), any());
        verify(attempts, never()).save(any());
    }

    private Assignment assignment() {
        Assignment assignment = new Assignment();
        assignment.setId("assignment-1");
        assignment.setTenantId("erg");
        assignment.setAcademicClassId("");
        assignment.setQuizId("quiz-1");
        assignment.setStudentIds(List.of("student-1"));
        return assignment;
    }

    private Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTenantId("erg");
        quiz.setKind("test");
        return quiz;
    }

    private QuizPackage packageEntity() {
        QuizPackage pkg = new QuizPackage();
        pkg.setId("pkg-server");
        pkg.setTenantId("erg");
        pkg.setQuizId("quiz-1");
        pkg.setQuizVersionId("version-1");
        pkg.setQuizVersion("7");
        pkg.setContentHash("hash-server");
        pkg.setGradingMode("server-authoritative");
        pkg.setPackageJson(Map.of("quiz", Map.of("sections", List.of(
                Map.of("questions", List.of("q1", "q2"))))));
        return pkg;
    }

    private Attempt attempt() {
        Attempt attempt = new Attempt();
        attempt.setId("attempt-1");
        attempt.setTenantId("erg");
        attempt.setAssignmentId("assignment-1");
        attempt.setQuizId("quiz-1");
        attempt.setStudentId("student-1");
        attempt.setPackageId("package-1");
        attempt.setPackageHash("hash-1");
        attempt.setStatus("in_progress");
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setStartedAt(SERVER_NOW);
        return attempt;
    }

    private UserAccessContext access() {
        return new UserAccessContext("student-1", "erg", List.of("*"), List.of("*"), null, null);
    }
}
