package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SaveAnswerRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.quiz.runtime.AttemptEventRecorder;
import com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LmsRuntimeServiceAttemptRulesTest {

    @Mock
    private LmsClassroomService classroomService;
    @Mock
    private LmsAssignmentService assignmentService;
    @Mock
    private LmsQuizService quizService;
    @Mock
    private LmsAttemptService attemptService;
    @Mock
    private LmsStudentService studentService;
    @Mock
    private QuizRuntimePackageService runtimePackageService;
    @Mock
    private QuizAttemptGradingService attemptGradingService;
    @Mock
    private AttemptEventRecorder attemptEventRecorder;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache lmsHomeworkWorkspaceCache;
    @Mock
    private Cache lmsClassWorkspaceCache;
    @Mock
    private Cache lmsAssignmentProgressCache;
    @Mock
    private Cache studentDashboardCache;
    @Mock
    private Cache quizAssignmentReportCache;
    @Mock
    private Cache quizQuestionAnalyticsCache;

    private LmsRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new LmsRuntimeService(
                classroomService,
                assignmentService,
                quizService,
                attemptService,
                studentService,
                runtimePackageService,
                attemptGradingService,
                attemptEventRecorder,
                cacheManager);
        when(cacheManager.getCache("lmsHomeworkWorkspace")).thenReturn(lmsHomeworkWorkspaceCache);
        when(cacheManager.getCache("lmsClassWorkspace")).thenReturn(lmsClassWorkspaceCache);
        when(cacheManager.getCache("lmsAssignmentProgress")).thenReturn(lmsAssignmentProgressCache);
        when(cacheManager.getCache("studentDashboard")).thenReturn(studentDashboardCache);
        when(cacheManager.getCache("quizAssignmentReport")).thenReturn(quizAssignmentReportCache);
        when(cacheManager.getCache("quizQuestionAnalytics")).thenReturn(quizQuestionAnalyticsCache);
        when(classroomService.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
        when(attemptService.save(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startAttemptRejectsPastDueAssignmentBeforeCreatingAttempt() {
        Assignment assignment = assignment();
        assignment.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.startAttempt("student-1", startRequest(), access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("past due");

        verify(attemptService, never()).save(any());
    }

    @Test
    void startAttemptRejectsNewAttemptWhenLimitIsReached() {
        Assignment assignment = assignment();
        assignment.setAttemptLimit(1);
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(attemptService.findInProgress("erg", "assignment-1", "quiz-1", "student-1"))
                .thenReturn(Optional.empty());
        when(attemptService.countCompletedForLimit("erg", "assignment-1", "quiz-1", "student-1"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.startAttempt("student-1", startRequest(), access()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("attempt limit");

        verify(attemptService, never()).save(any());
    }

    @Test
    void listStudentScoresUsesOneBatchAttemptLookup() {
        Assignment first = assignment();
        first.setId("assignment-1");
        Assignment second = assignment();
        second.setId("assignment-2");
        when(assignmentService.findByTenantIdOrderByCreatedAtDesc("erg")).thenReturn(List.of(first, second));
        when(attemptService.findTop3ByAssignmentsAndStudent("erg", List.of("assignment-1", "assignment-2"), "student-1"))
                .thenReturn(List.of());

        service.listStudentScores("student-1", access());

        verify(attemptService).findTop3ByAssignmentsAndStudent(
                "erg", List.of("assignment-1", "assignment-2"), "student-1");
        verify(attemptService, never()).findTop3ByAssignmentAndStudent(any(), any(), any());
    }

    @Test
    void listStudentAssignmentsResolvesTheStudentClassOnce() {
        Assignment first = assignment();
        first.setStudentIds(List.of());
        Assignment second = assignment();
        second.setId("assignment-2");
        second.setStudentIds(List.of());
        Student student = new Student();
        student.setAcademicClassId("class-1");
        when(assignmentService.findByTenantIdOrderByCreatedAtDesc("erg")).thenReturn(List.of(first, second));
        when(studentService.findFirstByTenantIdAndAuthUserId("erg", "student-1")).thenReturn(Optional.of(student));

        service.listStudentAssignments("student-1", access());

        verify(studentService, times(1)).findFirstByTenantIdAndAuthUserId("erg", "student-1");
    }

    @Test
    void pagedStudentAssignmentsUseCanonicalLmsStudentIdForRecipientJoin() {
        Student student = new Student();
        student.setId("lms-student-1");
        student.setAcademicClassId("class-1");
        when(studentService.findFirstByTenantIdAndAuthUserId("erg", "student-user-1"))
                .thenReturn(Optional.of(student));
        when(assignmentService.findStudentAssignments(
                org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq("lms-student-1"),
                org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listStudentAssignments("student-user-1", access(), 0, 50);

        verify(assignmentService).findStudentAssignments(
                org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq("lms-student-1"),
                org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void startAttemptReplaysExistingAttemptForIdempotencyKey() {
        Assignment assignment = assignment();
        Attempt attempt = attempt();
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(attemptService.findByStartIdempotencyKey("erg", "assignment-1", "quiz-1", "student-1", "start-key-1"))
                .thenReturn(Optional.of(attempt));

        var response = service.startAttempt("student-1", startRequest(), "start-key-1", access());

        assertThat(response.id()).isEqualTo("attempt-1");
        verify(attemptService, never()).save(any());
        verifyAttemptReadCachesNotCleared();
    }

    @Test
    void startAttemptEvictsAttemptReadCachesAfterPersistingNewAttempt() {
        Assignment assignment = assignment();
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(attemptService.findInProgress("erg", "assignment-1", "quiz-1", "student-1"))
                .thenReturn(Optional.empty());
        when(runtimePackageService.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.empty());
        when(quizService.findById("quiz-1")).thenReturn(Optional.of(quiz()));

        var response = service.startAttempt("student-1", startRequest(), access());

        assertThat(response.assignmentId()).isEqualTo("assignment-1");
        verifyAttemptReadCachesCleared();
    }

    @Test
    void saveAnswerEvictsAttemptReadCachesAfterPersistingAnswer() {
        Attempt attempt = attempt();
        when(attemptService.findById("attempt-1")).thenReturn(Optional.of(attempt));
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment()));

        service.saveAnswer(
                "attempt-1",
                "question-1",
                new SaveAnswerRequest("A", Map.of(), OffsetDateTime.now(ZoneOffset.UTC)),
                access());

        verifyAttemptReadCachesCleared();
    }

    @Test
    void submitAttemptRejectsWhenMaxDurationIsExceeded() {
        Assignment assignment = assignment();
        assignment.setMaxDurationMinutes(10);
        Attempt attempt = attempt();
        OffsetDateTime submittedAt = attempt.getStartedAt().plusMinutes(11);

        when(attemptService.findById("attempt-1")).thenReturn(Optional.of(attempt));
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submitAttempt(
                "attempt-1",
                new SubmitAttemptRequest(Map.of(), submittedAt, "hash-1", "1", Map.of(), List.of()),
                access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duration exceeded");

        verify(attemptService, never()).save(any());
        verifyAttemptReadCachesNotCleared();
    }

    @Test
    void submitAttemptEvictsAttemptReadCachesAfterProjectionRefresh() {
        Assignment assignment = assignment();
        Attempt attempt = attempt();
        Quiz quiz = quiz();
        quiz.setQuestionIds(List.of("question-1", "question-2"));
        when(attemptService.findById("attempt-1")).thenReturn(Optional.of(attempt));
        when(assignmentService.findById("assignment-1")).thenReturn(Optional.of(assignment));
        when(quizService.findById("quiz-1")).thenReturn(Optional.of(quiz));

        service.submitAttempt(
                "attempt-1",
                new SubmitAttemptRequest(Map.of("question-1", "A"), OffsetDateTime.now(ZoneOffset.UTC), "hash-1", "1", Map.of(), List.of()),
                access());

        verifyAttemptReadCachesCleared();
    }

    private void verifyAttemptReadCachesCleared() {
        verify(lmsHomeworkWorkspaceCache).clear();
        verify(lmsClassWorkspaceCache).clear();
        verify(lmsAssignmentProgressCache).clear();
        verify(studentDashboardCache).clear();
        verify(quizAssignmentReportCache).clear();
        verify(quizQuestionAnalyticsCache).clear();
    }

    private void verifyAttemptReadCachesNotCleared() {
        verifyNoInteractions(lmsHomeworkWorkspaceCache);
        verifyNoInteractions(lmsClassWorkspaceCache);
        verifyNoInteractions(lmsAssignmentProgressCache);
        verifyNoInteractions(studentDashboardCache);
        verifyNoInteractions(quizAssignmentReportCache);
        verifyNoInteractions(quizQuestionAnalyticsCache);
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

    private Attempt attempt() {
        Attempt attempt = new Attempt();
        attempt.setId("attempt-1");
        attempt.setTenantId("erg");
        attempt.setAssignmentId("assignment-1");
        attempt.setQuizId("quiz-1");
        attempt.setStudentId("student-1");
        attempt.setPackageHash("hash-1");
        attempt.setStatus("in_progress");
        attempt.setAnswers(Map.of());
        attempt.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return attempt;
    }

    private Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTenantId("erg");
        quiz.setKind("test");
        return quiz;
    }

    private StartAttemptRequest startRequest() {
        return new StartAttemptRequest("assignment-1", "quiz-1", "package-1", "hash-1");
    }

    private UserAccessContext access() {
        return new UserAccessContext("student-1", "erg", List.of("*"), List.of("*"), null, null);
    }
}
