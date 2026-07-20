package com.erg.ergspringboot.service.quiz.runtime;

import com.erg.ergspringboot.model.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.model.quiz.runtime.StudentProgress;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.repository.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.repository.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.service.quiz.runtime.GradingEngine.GradeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-authoritative grading of a submitted attempt against its pinned quiz version (spec §6).
 *
 * <p>Grades every auto-gradable question with {@link GradingEngine}, upserts a normalized
 * {@code attempt_answers} row per question (only overwriting when this grade is newer), sums awarded
 * points, and returns the recomputed score. Manual (essay) questions are recorded as needing review and
 * excluded from the auto total. Callers persist the score onto the attempt; this service owns the
 * per-question rows so reports read from a normalized source.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class QuizAttemptGradingService {

    public record GradedAttempt(
            BigDecimal score,
            BigDecimal maxScore,
            double percent,
            boolean passed,
            boolean needsManualGrade,
            int gradedCount,
            int totalQuestions
    ) {
    }

    private static final int SCALE = 2;
    private static final double PASS_PERCENT = 50.0;

    private final QuizVersionRepository quizVersionRepository;
    private final QuizVersionQuestionRepository questionRepository;
    private final AttemptAnswerRowRepository answerRowRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final GradingEngine gradingEngine;

    public QuizAttemptGradingService(
            QuizVersionRepository quizVersionRepository,
            QuizVersionQuestionRepository questionRepository,
            AttemptAnswerRowRepository answerRowRepository,
            StudentProgressRepository studentProgressRepository,
            GradingEngine gradingEngine
    ) {
        this.quizVersionRepository = quizVersionRepository;
        this.questionRepository = questionRepository;
        this.answerRowRepository = answerRowRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.gradingEngine = gradingEngine;
    }

    /**
     * Grades a submitted attempt. {@code answersByQuestionId} maps runtime question id -> raw answer.
     * Returns the recomputed score; when the pinned version is unavailable the caller keeps its own
     * fallback scoring.
     */
    public Optional<GradedAttempt> gradeSubmission(
            String tenantId,
            String attemptId,
            String quizVersionId,
            Map<String, Object> answersByQuestionId
    ) {
        if (quizVersionId == null || quizVersionId.isBlank()) {
            return Optional.empty();
        }
        List<QuizVersionQuestion> questions = questionRepository.findByQuizVersionIdOrderByOrderIndexAsc(quizVersionId);
        if (questions.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> answers = answersByQuestionId == null ? Map.of() : answersByQuestionId;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal awardedTotal = BigDecimal.ZERO;
        BigDecimal maxTotal = BigDecimal.ZERO;
        boolean anyManual = false;
        int gradedCount = 0;
        Map<String, AttemptAnswerRow> existingRows = new HashMap<>();
        List<String> questionIds = questions.stream().map(QuizVersionQuestion::getId).toList();
        answerRowRepository.findByAttemptIdAndQuizVersionQuestionIdIn(attemptId, questionIds)
                .forEach(row -> existingRows.put(row.getQuizVersionQuestionId(), row));
        List<AttemptAnswerRow> changedRows = new ArrayList<>(questions.size());

        for (QuizVersionQuestion question : questions) {
            BigDecimal maxPoints = question.getPoints() == null ? BigDecimal.ZERO : question.getPoints();
            // Survey (likert) questions score 0 and don't inflate the denominator (spec §6).
            if (!"survey".equals(question.getGradingPolicy())) {
                maxTotal = maxTotal.add(maxPoints);
            }
            Object rawAnswer = answers.get(question.getRuntimeQuestionId());

            if ("manual".equals(question.getGradingPolicy())) {
                anyManual = true;
                upsertAnswerRow(tenantId, attemptId, question, rawAnswer,
                        new GradeResult(null, false, BigDecimal.ZERO, true), "server", now,
                        existingRows, changedRows);
                continue;
            }
            if ("survey".equals(question.getGradingPolicy())) {
                upsertAnswerRow(tenantId, attemptId, question, rawAnswer,
                        new GradeResult(null, false, BigDecimal.ZERO, false), "server", now,
                        existingRows, changedRows);
                continue;
            }

            GradeResult result = gradingEngine.grade(
                    question.getKind(), rawAnswer, question.getAnswerKey(), maxPoints);
            awardedTotal = awardedTotal.add(result.awardedPoints());
            gradedCount++;
            upsertAnswerRow(tenantId, attemptId, question, rawAnswer, result, "server", now,
                    existingRows, changedRows);
        }
        if (!changedRows.isEmpty()) {
            answerRowRepository.saveAll(changedRows);
        }

        double percent = maxTotal.signum() > 0
                ? awardedTotal.divide(maxTotal, 6, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;
        percent = Math.min(100.0, Math.max(0.0, percent));
        boolean passed = !anyManual && percent >= PASS_PERCENT;

        return Optional.of(new GradedAttempt(
                awardedTotal.setScale(SCALE, RoundingMode.HALF_UP),
                maxTotal.setScale(SCALE, RoundingMode.HALF_UP),
                percent,
                passed,
                anyManual,
                gradedCount,
                questions.size()
        ));
    }

    private void upsertAnswerRow(
            String tenantId,
            String attemptId,
            QuizVersionQuestion question,
            Object rawAnswer,
            GradeResult result,
            String gradedBy,
            OffsetDateTime gradedAt,
            Map<String, AttemptAnswerRow> existingRows,
            List<AttemptAnswerRow> changedRows
    ) {
        AttemptAnswerRow row = existingRows.getOrDefault(question.getId(), new AttemptAnswerRow());
        // Only overwrite when this grade is at least as new (spec §6 only-if-newer upsert).
        if (row.getGradedAt() != null && gradedAt.isBefore(row.getGradedAt())) {
            return;
        }
        row.setAttemptId(attemptId);
        row.setQuizVersionQuestionId(question.getId());
        row.setTenantId(tenantId);
        row.setQuestionKind(question.getKind());
        row.setRawAnswer(rawAnswer);
        row.setCorrect(result.correct());
        row.setPartiallyRight(result.partiallyRight());
        row.setAwardedPoints(result.awardedPoints());
        row.setMaxPoints(question.getPoints() == null ? BigDecimal.ZERO : question.getPoints());
        row.setGradedBy(gradedBy);
        row.setGradedAt(gradedAt);
        row.setNeedsManualGrade(result.needsManualGrade());
        changedRows.add(row);
    }

    /** Upserts the live progress projection for the teacher dashboard (spec §1.7). */
    public void projectProgress(
            String tenantId,
            String studentUserId,
            String studentName,
            String quizId,
            String quizTitle,
            String assignmentId,
            String classId,
            String mode,
            String status,
            int answeredCount,
            int totalQuestions,
            BigDecimal score,
            Boolean passed,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        StudentProgress progress = studentProgressRepository
                .findByTenantIdAndStudentUserIdAndQuizIdAndAssignmentId(tenantId, studentUserId, quizId, assignmentId)
                .orElseGet(StudentProgress::new);
        progress.setTenantId(tenantId);
        progress.setStudentUserId(studentUserId);
        progress.setStudentName(studentName);
        progress.setQuizId(quizId);
        progress.setQuizTitle(quizTitle);
        progress.setAssignmentId(assignmentId);
        progress.setClassId(classId);
        progress.setMode(mode);
        progress.setStatus(status);
        progress.setAnsweredCount(answeredCount);
        progress.setTotalQuestions(totalQuestions);
        progress.setPercentComplete(totalQuestions > 0
                ? BigDecimal.valueOf(answeredCount * 100.0 / totalQuestions).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        progress.setScore(score);
        progress.setPassed(passed);
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(startedAt == null ? now : startedAt);
        }
        progress.setSubmittedAt(submittedAt);
        progress.setLastActiveAt(now);
        studentProgressRepository.save(progress);
    }
}
