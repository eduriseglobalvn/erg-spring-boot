package com.erg.ergspringboot.lms.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.LatestSubmittedAttemptResponse;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.AttemptAnswer;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LmsLatestSubmittedAttemptUseCaseTest {
    private AttemptRepository repository;
    private LmsStudentService students;
    private AttemptAnswerRowRepository rows;
    private QuizAttemptGradingService grading;
    private LmsLatestSubmittedAttemptUseCase useCase;
    private UserAccessContext access;

    @BeforeEach
    void setUp() {
        repository = mock(AttemptRepository.class);
        students = mock(LmsStudentService.class);
        rows = mock(AttemptAnswerRowRepository.class);
        access = mock(UserAccessContext.class);
        when(access.tenantId()).thenReturn("t1");
        Student student = new Student();
        student.setId("stu-1");
        when(students.findFirstByTenantIdAndAuthUserId("t1", "user-1")).thenReturn(Optional.of(student));
        grading = mock(QuizAttemptGradingService.class);
        useCase = new LmsLatestSubmittedAttemptUseCase(new LmsAttemptService(repository),
                new LmsStudentIdentityLookup(students), rows, grading,
                mock(com.erg.ergspringboot.lms.persistence.AssignmentRepository.class));
    }

    @Test
    void returnsNullWhenNothingWasHandedIn() {
        when(repository.findLatestByStatuses(org.mockito.ArgumentMatchers.eq("t1"), org.mockito.ArgumentMatchers.eq("a1"),
                org.mockito.ArgumentMatchers.eq("stu-1"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(useCase.latestSubmitted("a1", "user-1", access)).isNull();
    }

    @Test
    void queriesOnlyHandedInStatusesSoANewerInProgressAttemptIsIgnored() {
        Attempt submitted = attempt("att-old", "submitted");
        when(repository.findLatestByStatuses(org.mockito.ArgumentMatchers.eq("t1"), org.mockito.ArgumentMatchers.eq("a1"),
                org.mockito.ArgumentMatchers.eq("stu-1"), org.mockito.ArgumentMatchers.argThat(statuses ->
                        statuses.containsAll(List.of("submitted", "graded", "needs_review")) && !statuses.contains("in_progress")),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(submitted));

        LatestSubmittedAttemptResponse result = useCase.latestSubmitted("a1", "user-1", access);

        assertThat(result.attemptId()).isEqualTo("att-old");
        assertThat(result.status()).isEqualTo("submitted");
    }

    @Test
    void resultsAreKeyedByRuntimeQuestionIdAndCarryNoAnswerKey() {
        when(repository.findLatestByStatuses(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(attempt("att-1", "graded")));
        // The version's question ids come from the grading service's published-version cache, not a per-call reload.
        when(grading.runtimeIdsByVersionQuestionId("qv-1")).thenReturn(Map.of("vq-1", "q-1"));
        AttemptAnswerRow row = new AttemptAnswerRow();
        row.setAttemptId("att-1");
        row.setQuizVersionQuestionId("vq-1");
        row.setCorrect(true);
        row.setPartiallyRight(false);
        row.setAwardedPoints(new BigDecimal("2.00"));
        row.setMaxPoints(new BigDecimal("2.00"));
        row.setNeedsManualGrade(false);
        when(rows.findByAttemptId("att-1")).thenReturn(List.of(row));

        LatestSubmittedAttemptResponse result = useCase.latestSubmitted("a1", "user-1", access);

        assertThat(result.answers()).containsEntry("q-1", "B");
        assertThat(result.results()).containsOnlyKeys("q-1");
        var outcome = result.results().get("q-1");
        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.partiallyRight()).isFalse();
        assertThat(outcome.awardedPoints()).isEqualTo(2.0);
        assertThat(outcome.maxPoints()).isEqualTo(2.0);
        assertThat(outcome.needsManualGrade()).isFalse();
        assertThat(result.toString()).doesNotContain("answerKey").doesNotContain("policySnapshot");
        assertThat(LatestSubmittedAttemptResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("answerKey", "policySnapshot");
    }

    private static Attempt attempt(String id, String status) {
        Attempt a = new Attempt();
        a.setId(id);
        a.setAssignmentId("a1");
        a.setQuizId("quiz-1");
        a.setQuizVersionId("qv-1");
        a.setStudentId("stu-1");
        a.setStatus(status);
        a.setScore(2.0);
        a.setMaxScore(2.0);
        a.setPercent(100.0);
        a.setPassed(true);
        a.setSubmittedAt(OffsetDateTime.parse("2026-11-01T10:00:00Z"));
        AttemptAnswer answer = new AttemptAnswer();
        answer.setAnswer("B");
        a.setAnswers(Map.of("q-1", answer));
        return a;
    }
}
