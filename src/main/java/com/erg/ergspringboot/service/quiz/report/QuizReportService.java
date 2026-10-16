package com.erg.ergspringboot.service.quiz.report;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsRow;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.StudentProgressRow;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.quiz.runtime.StudentProgress;
import com.erg.ergspringboot.repository.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository.StudentProgressSummaryProjection;
import com.erg.ergspringboot.repository.quiz.runtime.projection.QuestionAnalyticsProjection;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting read model (spec §4.9). Reads only the normalized runtime tables: per-question difficulty
 * from {@code attempt_answers}, cohort progress from {@code student_progress}. Never re-derives figures
 * from the monolithic attempt blob, so a report is a bounded aggregate query, not a JSONB scan.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class QuizReportService {

    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_NEEDS_REVIEW = "needs_review";

    private final AttemptAnswerRowRepository answerRowRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final QuizVersionRepository quizVersionRepository;

    public QuizReportService(
            AttemptAnswerRowRepository answerRowRepository,
            StudentProgressRepository studentProgressRepository,
            QuizVersionRepository quizVersionRepository
    ) {
        this.answerRowRepository = answerRowRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.quizVersionRepository = quizVersionRepository;
    }

    /** Per-question analytics for the active published version of a quiz (spec §4.9). */
    @Cacheable(cacheNames = "quizQuestionAnalytics", key = "{#tenantId, #quizId}")
    public QuestionAnalyticsResponse questionAnalytics(String tenantId, String quizId) {
        String quizVersionId = quizVersionRepository
                .findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz has no active published version"))
                .getId();
        return questionAnalyticsByVersion(tenantId, quizVersionId);
    }

    @Cacheable(cacheNames = "quizQuestionAnalytics", key = "{#tenantId, #quizVersionId}")
    public QuestionAnalyticsResponse questionAnalyticsByVersion(String tenantId, String quizVersionId) {
        List<QuestionAnalyticsRow> rows = answerRowRepository
                .findQuestionAnalytics(tenantId, quizVersionId).stream()
                .map(this::toRow)
                .toList();
        return new QuestionAnalyticsResponse(quizVersionId, rows);
    }

    /** Live cohort progress for one assignment (spec §4.9), read straight from student_progress. */
    @Cacheable(cacheNames = "quizAssignmentReport", key = "{#tenantId, #assignmentId}")
    public AssignmentReportResponse assignmentReport(String tenantId, String assignmentId) {
        return assignmentReport(tenantId, assignmentId, 0, 100);
    }

    @Cacheable(cacheNames = "quizAssignmentReport", key = "{#tenantId, #assignmentId, #page, #size}")
    public AssignmentReportResponse assignmentReport(
            String tenantId, String assignmentId, Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 100 : Math.min(100, Math.max(1, size));
        Page<StudentProgress> pageResult = studentProgressRepository
                .findByTenantIdAndAssignmentIdOrderBySubmittedAtDesc(
                        tenantId, assignmentId, PageRequest.of(safePage, safeSize));
        // Repository paging is part of the report contract. Never fall back to the
        // legacy unbounded list method when a provider returns an unexpected null.
        List<StudentProgress> progress = pageResult == null ? List.of() : pageResult.getContent();

        StudentProgressSummaryProjection summary = studentProgressRepository
                .summarizeAssignmentAggregate(tenantId, assignmentId);
        if (summary == null) {
            // Rolling-deploy safety for a database that has not backfilled the aggregate yet.
            summary = studentProgressRepository.summarizeAssignment(tenantId, assignmentId);
        }
        long total = summary == null ? (pageResult == null ? progress.size() : pageResult.getTotalElements()) : summary.getTotalRecipients();
        int submitted = (int) (summary == null ? progress.stream().filter(this::isSubmitted).count() : summary.getSubmittedCount());
        int inProgress = (int) (summary == null ? progress.stream().filter(this::isInProgress).count() : summary.getInProgressCount());
        int needsReview = (int) (summary == null ? progress.stream().filter(this::isNeedsReview).count() : summary.getNeedsReviewCount());
        int notStarted = Math.max(0, (int) total - submitted - inProgress - needsReview);
        double averagePercent = summary == null ? averageScore(progress) : summary.getAveragePercent();

        List<StudentProgressRow> students = progress.stream().map(this::toProgressRow).toList();
        return new AssignmentReportResponse(
                assignmentId, (int) total, submitted, inProgress, notStarted, needsReview, averagePercent, students,
                safePage, safeSize, pageResult == null ? (total == 0 ? 0 : (int) Math.ceil((double) total / safeSize))
                        : pageResult.getTotalPages(), pageResult != null && pageResult.hasNext());
    }

    /** Filtered assignment progress for REST reporting and bounded exports. */
    public AssignmentReportResponse assignmentReport(
            String tenantId, String assignmentId, AssignmentProgressFilter filter) {
        AssignmentProgressFilter safe = filter == null
                ? new AssignmentProgressFilter(null, null, null, null, java.time.ZoneId.of("UTC"), 0, 100)
                : filter;
        Page<StudentProgress> pageResult = studentProgressRepository.findAssignmentReport(
                tenantId, assignmentId, safe.classId(), safe.status(), safe.submittedFrom(), safe.submittedTo(),
                PageRequest.of(safe.page(), safe.size()));
        StudentProgressSummaryProjection summary = studentProgressRepository.summarizeAssignment(
                tenantId, assignmentId, safe.classId(), safe.status(), safe.submittedFrom(), safe.submittedTo());
        List<StudentProgress> progress = pageResult == null ? List.of() : pageResult.getContent();
        long total = summary == null ? (pageResult == null ? 0 : pageResult.getTotalElements()) : summary.getTotalRecipients();
        int submitted = summary == null ? (int) progress.stream().filter(this::isSubmitted).count() : (int) summary.getSubmittedCount();
        int inProgress = summary == null ? (int) progress.stream().filter(this::isInProgress).count() : (int) summary.getInProgressCount();
        int needsReview = summary == null ? (int) progress.stream().filter(this::isNeedsReview).count() : (int) summary.getNeedsReviewCount();
        int notStarted = Math.max(0, (int) total - submitted - inProgress - needsReview);
        double averagePercent = summary == null ? averageScore(progress) : summary.getAveragePercent();
        int totalPages = pageResult == null ? (total == 0 ? 0 : (int) Math.ceil((double) total / safe.size())) : pageResult.getTotalPages();
        return new AssignmentReportResponse(
                assignmentId, (int) total, submitted, inProgress, notStarted, needsReview, averagePercent,
                progress.stream().map(this::toProgressRow).toList(), safe.page(), safe.size(), totalPages,
                pageResult != null && pageResult.hasNext());
    }

    private boolean isSubmitted(StudentProgress row) { return STATUS_SUBMITTED.equalsIgnoreCase(row.getStatus()); }
    private boolean isInProgress(StudentProgress row) { return STATUS_IN_PROGRESS.equalsIgnoreCase(row.getStatus()); }
    private boolean isNeedsReview(StudentProgress row) { return STATUS_NEEDS_REVIEW.equalsIgnoreCase(row.getStatus()); }
    private double averageScore(List<StudentProgress> rows) {
        return rows.stream().map(StudentProgress::getScore).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
    }

    private QuestionAnalyticsRow toRow(QuestionAnalyticsProjection projection) {
        long answered = projection.getAnsweredCount();
        long correct = projection.getCorrectCount();
        double correctRate = answered == 0 ? 0.0 : (double) correct / answered;
        return new QuestionAnalyticsRow(
                projection.getQuestionId(),
                projection.getQuestionKind(),
                answered,
                correct,
                projection.getPartialCount(),
                correctRate,
                doubleValue(projection.getAvgAwardedPoints()),
                doubleValue(projection.getMaxPoints())
        );
    }

    private StudentProgressRow toProgressRow(StudentProgress row) {
        return new StudentProgressRow(
                row.getStudentUserId(),
                row.getStudentName(),
                row.getStatus(),
                row.getAnsweredCount() == null ? 0 : row.getAnsweredCount(),
                row.getTotalQuestions() == null ? 0 : row.getTotalQuestions(),
                row.getPercentComplete() == null ? null : row.getPercentComplete().doubleValue(),
                row.getScore() == null ? null : row.getScore().doubleValue(),
                row.getPassed(),
                row.getSubmittedAt() == null ? null : row.getSubmittedAt().toString()
        );
    }

    private double doubleValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
