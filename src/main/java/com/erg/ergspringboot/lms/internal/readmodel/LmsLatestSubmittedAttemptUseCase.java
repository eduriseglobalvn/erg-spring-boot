package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.LatestSubmittedAttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuestionOutcome;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own latest handed-in attempt with per-question correctness; exposes no answer key. */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsLatestSubmittedAttemptUseCase {
    private final LmsAttemptService attempts;
    private final LmsStudentIdentityLookup identities;
    private final AttemptAnswerRowRepository answerRows;
    private final QuizAttemptGradingService grading;
    private final AssignmentRepository assignments;

    public LmsLatestSubmittedAttemptUseCase(LmsAttemptService attempts, LmsStudentIdentityLookup identities,
            AttemptAnswerRowRepository answerRows, QuizAttemptGradingService grading,
            AssignmentRepository assignments) {
        this.attempts = attempts; this.identities = identities; this.answerRows = answerRows; this.grading = grading;
        this.assignments = assignments;
    }

    /** Returns null when the caller has no handed-in attempt on the assignment (including an assignment that is not theirs). */
    public LatestSubmittedAttemptResponse latestSubmitted(String assignmentId, String authUserId, UserAccessContext access) {
        String tenant = AuthenticatedTenant.require(access.tenantId());
        // Attempts store Student.id; resolve the caller's Student exactly like listScores does.
        var identity = identities.byAuthUser(tenant, authUserId);
        String learnerKey = identity.exists() ? identity.studentId() : authUserId;
        Attempt attempt = attempts.findLatestSubmitted(tenant, assignmentId, learnerKey).orElse(null);
        if (attempt == null) return null;

        Map<String, Object> answers = new LinkedHashMap<>();
        if (attempt.getAnswers() != null) attempt.getAnswers().forEach((id, value) -> answers.put(id, value == null ? null : value.getAnswer()));

        // attempt_answers is keyed by quiz_version_question_id; the player/answers use the runtime question id.
        Map<String, QuestionOutcome> results = new LinkedHashMap<>();
        if (attempt.getQuizVersionId() != null && !attempt.getQuizVersionId().isBlank()) {
            // A published version is immutable: its question ids come from the grading service's version cache.
            Map<String, String> runtimeIds = grading.runtimeIdsByVersionQuestionId(attempt.getQuizVersionId());
            for (AttemptAnswerRow row : answerRows.findByAttemptId(attempt.getId())) {
                String runtimeId = runtimeIds.get(row.getQuizVersionQuestionId());
                if (runtimeId == null) continue;
                results.put(runtimeId, new QuestionOutcome(Boolean.TRUE.equals(row.getCorrect()),
                        Boolean.TRUE.equals(row.getPartiallyRight()), num(row.getAwardedPoints()), num(row.getMaxPoints()),
                        Boolean.TRUE.equals(row.getNeedsManualGrade())));
            }
        }
        // Same rule as starting an attempt, so the review screen can hide «Làm lại» (BUG-A-058).
        Integer limit = assignments.findByTenantIdAndId(tenant, assignmentId).map(a -> a.getAttemptLimit()).orElse(null);
        boolean exhausted = limit != null && limit > 0
                && attempts.countCompletedForLimit(tenant, assignmentId, attempt.getQuizId(), learnerKey) >= limit;
        return new LatestSubmittedAttemptResponse(attempt.getId(), attempt.getAssignmentId(), attempt.getQuizId(),
                attempt.getQuizVersionId(), attempt.getPackageHash(), attempt.getStatus(), attempt.getScore(),
                attempt.getMaxScore(), attempt.getPercent(), attempt.getPassed(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), answers, results, exhausted);
    }

    private static double num(java.math.BigDecimal value) { return value == null ? 0 : value.doubleValue(); }
}
