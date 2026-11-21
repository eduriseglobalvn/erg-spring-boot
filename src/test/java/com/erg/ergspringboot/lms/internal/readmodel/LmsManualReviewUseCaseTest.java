package com.erg.ergspringboot.lms.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LmsManualReviewUseCaseTest {
    private final LmsAttemptService attempts = mock(LmsAttemptService.class);
    private final LmsAssignmentAccessPolicy policy = mock(LmsAssignmentAccessPolicy.class);
    private final AttemptAnswerRowRepository answerRows = mock(AttemptAnswerRowRepository.class);
    private final QuizVersionQuestionRepository versionQuestions = mock(QuizVersionQuestionRepository.class);
    private final UserAccessContext access = mock(UserAccessContext.class);
    private final LmsManualReviewUseCase useCase = new LmsManualReviewUseCase(attempts, policy, answerRows, versionQuestions);

    @BeforeEach
    void attempt() {
        when(access.tenantId()).thenReturn("erg");
        Attempt attempt = mock(Attempt.class);
        when(attempt.getId()).thenReturn("att-1");
        when(attempt.getAssignmentId()).thenReturn("asg-1");
        when(attempt.getStudentId()).thenReturn("stu-1");
        when(attempt.getQuizVersionId()).thenReturn("ver-1");
        when(attempt.getStatus()).thenReturn("needs_review");
        when(attempt.getRecordVersion()).thenReturn(4L);
        when(attempts.findByTenantIdAndId("erg", "att-1")).thenReturn(Optional.of(attempt));
        when(policy.assignment("erg", "asg-1")).thenReturn(mock(Assignment.class));
    }

    @Test
    void listsOnlyHandGradedAnswersWithPromptAndPoints() {
        when(policy.isReviewer()).thenReturn(true);
        // Built first: a mock stubbed inside another thenReturn(...) breaks Mockito.
        List<QuizVersionQuestion> questions = List.of(
                question("vq-1", "q-choice", "single_choice", "Chọn đáp án"),
                question("vq-2", "q-essay", "essay", "Nêu hai nguỵ biện"));
        when(versionQuestions.findAllById(java.util.Set.of("vq-2"))).thenReturn(questions);
        when(answerRows.findByAttemptId("att-1")).thenReturn(List.of(
                row("vq-1", false, "server", "10"), row("vq-2", true, null, "0")));

        var response = useCase.manualAnswers("att-1", access);

        assertThat(response.version()).isEqualTo(4L);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.questionId()).isEqualTo("q-essay");
            assertThat(item.prompt()).isEqualTo("Nêu hai nguỵ biện");
            assertThat(item.maxPoints()).isEqualTo(10d);
            assertThat(item.needsManualGrade()).isTrue();
        });
    }

    @Test
    void keepsAnAnswerATeacherAlreadyScored() {
        when(policy.isReviewer()).thenReturn(true);
        List<QuizVersionQuestion> questions = List.of(question("vq-2", "q-essay", "essay", "Nêu hai nguỵ biện"));
        when(versionQuestions.findAllById(java.util.Set.of("vq-2"))).thenReturn(questions);
        when(answerRows.findByAttemptId("att-1")).thenReturn(List.of(row("vq-2", false, "teacher-1", "7")));

        assertThat(useCase.manualAnswers("att-1", access).items()).singleElement().satisfies(item -> {
            assertThat(item.needsManualGrade()).isFalse();
            assertThat(item.awardedPoints()).isEqualTo(7d);
        });
    }

    @Test
    void rejectsACallerWithoutAGradingPermission() {
        when(policy.isReviewer()).thenReturn(false);
        assertThatThrownBy(() -> useCase.manualAnswers("att-1", access)).isInstanceOf(ForbiddenException.class);
    }

    private static QuizVersionQuestion question(String id, String runtimeId, String kind, String title) {
        QuizVersionQuestion question = mock(QuizVersionQuestion.class);
        when(question.getId()).thenReturn(id);
        when(question.getQuizVersionId()).thenReturn("ver-1");
        when(question.getRuntimeQuestionId()).thenReturn(runtimeId);
        when(question.getKind()).thenReturn(kind);
        when(question.getPoints()).thenReturn(new BigDecimal("10"));
        when(question.getBody()).thenReturn(Map.of("title", title));
        return question;
    }

    private static AttemptAnswerRow row(String versionQuestionId, boolean needsManualGrade, String gradedBy, String awarded) {
        AttemptAnswerRow row = new AttemptAnswerRow();
        row.setAttemptId("att-1");
        row.setQuizVersionQuestionId(versionQuestionId);
        row.setNeedsManualGrade(needsManualGrade);
        row.setGradedBy(gradedBy);
        row.setAwardedPoints(new BigDecimal(awarded));
        row.setRawAnswer(Map.of("essayText", "bài làm"));
        return row;
    }
}
