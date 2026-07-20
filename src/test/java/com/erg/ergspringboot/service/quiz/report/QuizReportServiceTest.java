package com.erg.ergspringboot.service.quiz.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.quiz.runtime.StudentProgress;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository.AssignmentAggregateProjection;
import com.erg.ergspringboot.repository.quiz.runtime.projection.QuestionAnalyticsProjection;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizReportServiceTest {

    @Mock private AttemptAnswerRowRepository answerRowRepository;
    @Mock private StudentProgressRepository studentProgressRepository;
    @Mock private QuizVersionRepository quizVersionRepository;

    private QuizReportService service() {
        return new QuizReportService(answerRowRepository, studentProgressRepository, quizVersionRepository);
    }

    @Test
    void questionAnalyticsComputesCorrectRateFromNormalizedRows() {
        when(answerRowRepository.findQuestionAnalytics("tenant-a", "ver-1"))
                .thenReturn(List.of(analytics("q1", "single_choice", 10, 7, 0, new BigDecimal("0.70"), new BigDecimal("1.00"))));

        QuestionAnalyticsResponse response = service().questionAnalyticsByVersion("tenant-a", "ver-1");

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().correctRate()).isEqualTo(0.7);
        assertThat(response.questions().getFirst().answeredCount()).isEqualTo(10);
    }

    @Test
    void questionAnalyticsByQuizResolvesActiveVersion() {
        QuizVersion version = new QuizVersion();
        version.setId("ver-active");
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version));
        when(answerRowRepository.findQuestionAnalytics("tenant-a", "ver-active")).thenReturn(List.of());

        QuestionAnalyticsResponse response = service().questionAnalytics("tenant-a", "quiz-1");

        assertThat(response.quizVersionId()).isEqualTo("ver-active");
    }

    @Test
    void questionAnalyticsFailsWhenNoActiveVersion() {
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-x", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().questionAnalytics("tenant-a", "quiz-x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignmentReportBucketsStatusesAndAveragesScores() {
        List<StudentProgress> expected = List.of(
                        progress("s1", "submitted", new BigDecimal("80")),
                        progress("s2", "submitted", new BigDecimal("60")),
                        progress("s3", "in_progress", null),
                        progress("s4", "needs_review", null)
                );
        when(studentProgressRepository.findByTenantIdAndAssignmentIdOrderBySubmittedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("asg-1"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(expected));

        AssignmentReportResponse report = service().assignmentReport("tenant-a", "asg-1");

        assertThat(report.totalRecipients()).isEqualTo(4);
        assertThat(report.submittedCount()).isEqualTo(2);
        assertThat(report.inProgressCount()).isEqualTo(1);
        assertThat(report.needsReviewCount()).isEqualTo(1);
        assertThat(report.notStartedCount()).isZero();
        assertThat(report.averagePercent()).isEqualTo(70.0);
        assertThat(report.students()).hasSize(4);
    }

    @Test
    void assignmentReportUsesShardedAggregateInsteadOfRescanningProgressRows() {
        when(studentProgressRepository.findByTenantIdAndAssignmentIdOrderBySubmittedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("asg-aggregate"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(studentProgressRepository.summarizeAssignmentAggregate("tenant-a", "asg-aggregate"))
                .thenReturn(aggregate(5000, 4200, 600, 100, 82.5));

        AssignmentReportResponse report = service().assignmentReport("tenant-a", "asg-aggregate");

        assertThat(report.totalRecipients()).isEqualTo(5000);
        assertThat(report.notStartedCount()).isEqualTo(100);
        assertThat(report.averagePercent()).isEqualTo(82.5);
        verify(studentProgressRepository, never()).summarizeAssignment("tenant-a", "asg-aggregate");
    }

    @Test
    void reportReadModelsDeclareTenantScopedCacheKeys() throws NoSuchMethodException {
        Cacheable analyticsCache = QuizReportService.class
                .getMethod("questionAnalyticsByVersion", String.class, String.class)
                .getAnnotation(Cacheable.class);
        Cacheable assignmentCache = QuizReportService.class
                .getMethod("assignmentReport", String.class, String.class, Integer.class, Integer.class)
                .getAnnotation(Cacheable.class);

        assertThat(analyticsCache.cacheNames()).containsExactly("quizQuestionAnalytics");
        assertThat(analyticsCache.key()).isEqualTo("{#tenantId, #quizVersionId}");
        assertThat(assignmentCache.cacheNames()).containsExactly("quizAssignmentReport");
        assertThat(assignmentCache.key()).isEqualTo("{#tenantId, #assignmentId, #page, #size}");
    }

    @Test
    void convenienceReportEntrypointsAreCachedToo() throws NoSuchMethodException {
        Cacheable analyticsCache = QuizReportService.class
                .getMethod("questionAnalytics", String.class, String.class)
                .getAnnotation(Cacheable.class);
        Cacheable assignmentCache = QuizReportService.class
                .getMethod("assignmentReport", String.class, String.class)
                .getAnnotation(Cacheable.class);

        assertThat(analyticsCache.cacheNames()).containsExactly("quizQuestionAnalytics");
        assertThat(analyticsCache.key()).isEqualTo("{#tenantId, #quizId}");
        assertThat(assignmentCache.cacheNames()).containsExactly("quizAssignmentReport");
        assertThat(assignmentCache.key()).isEqualTo("{#tenantId, #assignmentId}");
    }

    private QuestionAnalyticsProjection analytics(
            String questionId, String kind, long answered, long correct, long partial,
            BigDecimal avgAwarded, BigDecimal maxPoints
    ) {
        return new QuestionAnalyticsProjection() {
            public String getQuestionId() { return questionId; }
            public String getQuestionKind() { return kind; }
            public long getAnsweredCount() { return answered; }
            public long getCorrectCount() { return correct; }
            public long getPartialCount() { return partial; }
            public long getNeedsManualCount() { return 0; }
            public BigDecimal getAvgAwardedPoints() { return avgAwarded; }
            public BigDecimal getMaxPoints() { return maxPoints; }
        };
    }

    private StudentProgress progress(String studentId, String status, BigDecimal score) {
        StudentProgress progress = new StudentProgress();
        progress.setStudentUserId(studentId);
        progress.setStatus(status);
        progress.setScore(score);
        progress.setAnsweredCount(0);
        progress.setTotalQuestions(0);
        return progress;
    }

    private AssignmentAggregateProjection aggregate(
            long total, long submitted, long inProgress, long needsReview, double average) {
        return new AssignmentAggregateProjection() {
            public long getTotalRecipients() { return total; }
            public long getSubmittedCount() { return submitted; }
            public long getInProgressCount() { return inProgress; }
            public long getNeedsReviewCount() { return needsReview; }
            public double getAveragePercent() { return average; }
            public long getSourceVersion() { return 1; }
        };
    }
}
