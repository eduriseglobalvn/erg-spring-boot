package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ManualReviewItem;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ManualReviewResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptAnswerRowRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a teacher needs to grade one attempt by hand: each essay answer with its prompt, and whether it still waits. */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsManualReviewUseCase {
    private final LmsAttemptService attempts;
    private final LmsAssignmentAccessPolicy policy;
    private final AttemptAnswerRowRepository answerRows;
    private final QuizVersionQuestionRepository versionQuestions;

    public LmsManualReviewUseCase(LmsAttemptService attempts, LmsAssignmentAccessPolicy policy,
            AttemptAnswerRowRepository answerRows, QuizVersionQuestionRepository versionQuestions) {
        this.attempts = attempts; this.policy = policy; this.answerRows = answerRows; this.versionQuestions = versionQuestions;
    }

    /** Same gate as grading: a review permission plus class scope over the learner of the attempt. */
    public ManualReviewResponse manualAnswers(String attemptId, UserAccessContext access) {
        String tenant = AuthenticatedTenant.require(access.tenantId());
        Attempt attempt = attempts.findByTenantIdAndId(tenant, attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        Assignment assignment = policy.assignment(tenant, attempt.getAssignmentId());
        if (!policy.isReviewer()) throw new ForbiddenException("Grading requires a teacher grading permission");
        policy.ensureReviewScope(assignment, attempt.getStudentId(), access);

        Map<String, AttemptAnswerRow> rows = new HashMap<>();
        answerRows.findByAttemptId(attempt.getId()).forEach(row -> rows.put(row.getQuizVersionQuestionId(), row));
        List<ManualReviewItem> items = new ArrayList<>();
        String versionId = attempt.getQuizVersionId();
        if (versionId != null && !versionId.isBlank()) {
            for (QuizVersionQuestion question : versionQuestions.findByQuizVersionIdOrderByOrderIndexAsc(versionId)) {
                AttemptAnswerRow row = rows.get(question.getId());
                // A hand-graded answer is one still waiting, or one a teacher (not the auto-grader, "server") has scored.
                if (row == null || !(Boolean.TRUE.equals(row.getNeedsManualGrade())
                        || (row.getGradedBy() != null && !"server".equals(row.getGradedBy())))) continue;
                Object title = question.getBody() == null ? null : question.getBody().get("title");
                items.add(new ManualReviewItem(question.getRuntimeQuestionId(), question.getKind(),
                        title == null ? "" : title.toString(), row.getRawAnswer(), num(question.getPoints()),
                        num(row.getAwardedPoints()), Boolean.TRUE.equals(row.getNeedsManualGrade())));
            }
        }
        return new ManualReviewResponse(attempt.getId(), attempt.getStudentId(), attempt.getStatus(),
                attempt.getRecordVersion() == null ? 0L : attempt.getRecordVersion(), items);
    }

    private static double num(BigDecimal value) { return value == null ? 0 : value.doubleValue(); }
}
