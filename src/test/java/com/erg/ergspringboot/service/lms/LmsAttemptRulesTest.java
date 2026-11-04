package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SaveAnswerRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.lms.LmsAttemptFixture;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class LmsAttemptRulesTest {

    private static final List<String> ATTEMPT_CACHES = List.of("lmsHomeworkWorkspace", "lmsClassWorkspace",
            "lmsAssignmentProgress", "studentDashboard", "quizAssignmentReport", "quizQuestionAnalytics");

    private LmsAttemptFixture fixture;
    private LmsAttemptUseCase service;
    private Map<String, Cache> caches;

    @BeforeEach
    void setUp() {
        fixture = new LmsAttemptFixture();
        service = fixture.useCase(Clock.systemUTC());
        caches = new java.util.LinkedHashMap<>();
        for (String name : ATTEMPT_CACHES) {
            Cache cache = org.mockito.Mockito.mock(Cache.class);
            caches.put(name, cache);
            when(fixture.caches.getCache(name)).thenReturn(cache);
        }
        when(fixture.classrooms.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
    }

    @Test
    void startAttemptRejectsPastDueAssignmentBeforeCreatingAttempt() {
        Assignment assignment = assignment();
        assignment.setDueAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.start("student-1", startRequest(), null, access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("past due");

        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void startAttemptRejectsNewAttemptWhenLimitIsReached() {
        Assignment assignment = assignment();
        assignment.setAttemptLimit(1);
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(fixture.attempts.countCompletedForLimit("erg", "assignment-1", "quiz-1", "student-1"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.start("student-1", startRequest(), null, access()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("attempt limit");

        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void pagedStudentAssignmentsUseCanonicalLmsStudentIdForRecipientJoin() {
        Student student = new Student();
        student.setId("lms-student-1");
        student.setAcademicClassId("class-1");
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "student-user-1"))
                .thenReturn(Optional.of(student));
        when(fixture.assignments.findStudentAssignments(
                org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq("lms-student-1"),
                org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        readModel().listAssignments("student-user-1", access(), 0, 50);

        verify(fixture.assignments).findStudentAssignments(
                org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq("lms-student-1"),
                org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void scoresResolveTheCallersStudentBeforeLookingUpAssignmentsAndAttempts() {
        Student student = new Student();
        student.setId("lms-student-1");
        student.setAcademicClassId("class-1");
        Assignment assignment = assignment();
        when(fixture.students.findFirstByTenantIdAndAuthUserId("erg", "student-user-1")).thenReturn(Optional.of(student));
        when(fixture.assignments.findStudentAssignments(org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq("lms-student-1"), org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(assignment)));
        Attempt attempt = attempt();
        attempt.setStudentId("lms-student-1");
        attempt.setScore(7d);
        when(fixture.attempts.findTop3ByAssignmentsAndStudent("erg", List.of("assignment-1"), "lms-student-1"))
                .thenReturn(List.of(attempt));

        var scores = readModel().listScores("student-user-1", access(), 0, 50);

        assertThat(scores.items()).hasSize(1);
        assertThat(scores.items().getFirst().bestScore()).isEqualTo(7d);
        // N5: the learner view never exposes the other recipients' internal student ids.
        assertThat(scores.items().getFirst().assignment().studentIds()).isEmpty();
    }

    @Test
    void startAttemptReplaysExistingAttemptForIdempotencyKey() {
        Assignment assignment = assignment();
        Attempt attempt = attempt();
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(fixture.attempts.findByStartIdempotencyKey("erg", "assignment-1", "quiz-1", "student-1", "start-key-1"))
                .thenReturn(Optional.of(attempt));

        var response = service.start("student-1", startRequest(), "start-key-1", access());

        assertThat(response.id()).isEqualTo("attempt-1");
        verify(fixture.attempts, never()).save(any());
        verifyAttemptReadCachesNotCleared();
    }

    @Test
    void startAttemptEvictsAttemptReadCachesAfterPersistingNewAttempt() {
        Assignment assignment = assignment();
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(fixture.packages.activePackageEntity("erg", "quiz-1")).thenReturn(Optional.empty());
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz()));

        var response = service.start("student-1", startRequest(), null, access());

        assertThat(response.assignmentId()).isEqualTo("assignment-1");
        verifyAttemptReadCachesCleared();
    }

    @Test
    void runtimeDraftAnswerAndSyncDoNotPersistBeforeSubmit() {
        Attempt attempt = attempt();
        when(fixture.attempts.findByTenantIdAndId("erg", "attempt-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment()));

        var answer = service.saveAnswer(
                "attempt-1",
                "question-1",
                new SaveAnswerRequest("A", Map.of(), OffsetDateTime.now(ZoneOffset.UTC)),
                access());
        service.saveDraft(
                "attempt-1",
                new AttemptDraftRequest(Map.of("question-1", "A"), "hash-1", "1", List.of(), Map.of()),
                access());
        var sync = service.sync(
                "attempt-1",
                new AttemptSyncRequest("hash-1", "1", Map.of(), List.of(Map.of("id", "event-1")), Map.of()),
                access());

        assertThat(answer.saved()).isFalse();
        assertThat(sync.conflicts()).isEmpty();
        verify(fixture.attempts, never()).save(any());
        verifyNoInteractions(fixture.events);
        verifyAttemptReadCachesNotCleared();
    }

    @Test
    void submitAttemptRejectsWhenMaxDurationAndGraceAreExceeded() {
        Assignment assignment = assignment();
        assignment.setMaxDurationMinutes(10);
        Attempt attempt = attempt();
        // Server time is authoritative; a client-supplied submittedAt must not backdate an expired attempt.
        attempt.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(12));
        OffsetDateTime submittedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(4);

        when(fixture.attempts.findByTenantIdAndId("erg", "attempt-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submit(
                "attempt-1",
                new SubmitAttemptRequest(Map.of(), submittedAt, "hash-1", "1", Map.of(), List.of()),
                access()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duration exceeded");

        verify(fixture.attempts, never()).save(any());
        verifyAttemptReadCachesNotCleared();
    }

    @Test
    void submitAttemptEvictsAttemptReadCachesAfterProjectionRefresh() {
        Assignment assignment = assignment();
        Attempt attempt = attempt();
        Quiz quiz = quiz();
        quiz.setQuestionIds(List.of("question-1", "question-2"));
        when(fixture.attempts.findByTenantIdAndId("erg", "attempt-1")).thenReturn(Optional.of(attempt));
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
        when(fixture.quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(quiz));

        service.submit(
                "attempt-1",
                new SubmitAttemptRequest(Map.of("question-1", "A"), OffsetDateTime.now(ZoneOffset.UTC), "hash-1", "1", Map.of(), List.of()),
                access());

        verifyAttemptReadCachesCleared();
    }

    private LmsStudentReadModelUseCase readModel() {
        return new LmsStudentReadModelUseCase(fixture.classrooms, fixture.assignments, fixture.attempts,
                fixture.students, fixture.quizzes, fixture.policy);
    }

    private void verifyAttemptReadCachesCleared() {
        caches.values().forEach(cache -> verify(cache).clear());
    }

    private void verifyAttemptReadCachesNotCleared() {
        caches.values().forEach(org.mockito.Mockito::verifyNoInteractions);
    }

    private Assignment assignment() {
        Assignment assignment = new Assignment();
        assignment.setId("assignment-1");
        assignment.setTenantId("erg");
        assignment.setAcademicClassId("");
        assignment.setQuizId("quiz-1");
        assignment.setStatus("active");
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
