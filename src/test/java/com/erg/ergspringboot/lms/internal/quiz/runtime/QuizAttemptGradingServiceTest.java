package com.erg.ergspringboot.lms.internal.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.repository.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizAttemptGradingServiceTest {
    private final QuizVersionRepository versions = mock(QuizVersionRepository.class);
    private final QuizVersionQuestionRepository questions = mock(QuizVersionQuestionRepository.class);
    private final AttemptAnswerRowRepository rows = mock(AttemptAnswerRowRepository.class);
    private QuizAttemptGradingService service;

    @BeforeEach
    void setUp() {
        service = new QuizAttemptGradingService(versions, questions, rows, mock(StudentProgressRepository.class), new GradingEngine());
    }

    @Test
    void passThresholdComesFromVersionSettingsInsteadOfFixedFiftyPercent() {
        QuizVersion version = new QuizVersion();
        version.setRuntimeQuiz(Map.of("settings", Map.of("runtime", Map.of("passPercent", 80))));
        when(versions.findById("ver-1")).thenReturn(Optional.of(version));
        when(questions.findByQuizVersionIdOrderByOrderIndexAsc("ver-1"))
                .thenReturn(List.of(choice("vq-1", "q1"), choice("vq-2", "q2"), choice("vq-3", "q3")));
        when(rows.findByAttemptIdAndQuizVersionQuestionIdIn(eq("att-1"), anyCollection())).thenReturn(List.of());

        var graded = service.gradeSubmission("erg", "att-1", "ver-1", Map.of(
                "q1", Map.of("choiceId", "b"), "q2", Map.of("choiceId", "b"), "q3", Map.of("choiceId", "a"))).orElseThrow();

        assertThat(graded.percent()).isBetween(66.0, 67.0);
        assertThat(graded.passed()).isFalse();
    }

    @Test
    void manualGradeRecomputesTotalsAndClearsPendingFlag() {
        when(versions.findById("ver-1")).thenReturn(Optional.empty());
        QuizVersionQuestion essay = choice("vq-essay", "essay-1");
        essay.setKind("essay");
        essay.setGradingPolicy("manual");
        essay.setPoints(BigDecimal.valueOf(2));
        QuizVersionQuestion auto = choice("vq-1", "q1");
        when(questions.findByQuizVersionIdOrderByOrderIndexAsc("ver-1")).thenReturn(List.of(auto, essay));
        AttemptAnswerRow autoRow = row("vq-1", BigDecimal.ONE, false);
        AttemptAnswerRow essayRow = row("vq-essay", BigDecimal.ZERO, true);
        when(rows.findByAttemptIdAndQuizVersionQuestionIdIn(eq("att-1"), anyCollection())).thenReturn(List.of(autoRow, essayRow));
        when(rows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var graded = service.gradeManualAnswer("erg", "att-1", "ver-1", "essay-1", BigDecimal.valueOf(2), "teacher-1");

        assertThat(graded.needsManualGrade()).isFalse();
        assertThat(graded.score()).isEqualByComparingTo("3");
        assertThat(graded.maxScore()).isEqualByComparingTo("3");
        assertThat(graded.passed()).isTrue();
        assertThat(essayRow.getNeedsManualGrade()).isFalse();
        assertThat(essayRow.getGradedBy()).isEqualTo("teacher-1");
        assertThatThrownBy(() -> service.gradeManualAnswer("erg", "att-1", "ver-1", "essay-1", BigDecimal.TEN, "teacher-1"))
                .isInstanceOf(BadRequestException.class);
    }

    private QuizVersionQuestion choice(String id, String runtimeId) {
        QuizVersionQuestion question = new QuizVersionQuestion();
        question.setId(id);
        question.setRuntimeQuestionId(runtimeId);
        question.setKind("single_choice");
        question.setGradingPolicy("auto");
        question.setPoints(BigDecimal.ONE);
        question.setAnswerKey(Map.of("correctChoiceId", "b"));
        return question;
    }

    private AttemptAnswerRow row(String questionId, BigDecimal awarded, boolean manual) {
        AttemptAnswerRow row = new AttemptAnswerRow();
        row.setAttemptId("att-1");
        row.setQuizVersionQuestionId(questionId);
        row.setAwardedPoints(awarded);
        row.setNeedsManualGrade(manual);
        return row;
    }
}
