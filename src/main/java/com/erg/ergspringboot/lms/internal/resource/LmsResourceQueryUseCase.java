package com.erg.ergspringboot.lms.internal.resource;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsCallerPermissions;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Read-only quiz resource boundary; published packages are preferred over legacy embedded blobs.
 *
 * <p>Authoring readers ({@code lms.quiz.read}) see the active package. Learners ({@code lms.quiz.play} only)
 * must be a recipient of a visible assignment that distributes the quiz, receive the version pinned by that
 * assignment, and never receive the answer key unless the assignment explicitly allows offline grading.
 */
@Service
public class LmsResourceQueryUseCase {
    private static final String[] AUTHORING_READ = {"lms.quiz.read", "*"};
    private final LmsQuizService quizzes;
    private final QuizRuntimePackageService packages;
    private final LmsAssignmentAccessPolicy policy;
    private final LmsCallerPermissions permissions;

    public LmsResourceQueryUseCase(LmsQuizService quizzes, QuizRuntimePackageService packages,
            LmsAssignmentAccessPolicy policy, LmsCallerPermissions permissions) {
        this.quizzes = quizzes; this.packages = packages; this.policy = policy; this.permissions = permissions;
    }

    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        return getQuizPackage(quizId, null, access);
    }

    public QuizPackageResponse getQuizPackage(String quizId, String assignmentId, UserAccessContext access) {
        String tenant = tenant(access.tenantId());
        if (permissions.hasAny(AUTHORING_READ)) {
            QuizPackageResponse published = packages.activePackage(tenant, quizId).orElse(null);
            return published != null ? published : legacy(requireQuiz(tenant, quizId));
        }
        return learnerPackage(tenant, quizId, blank(assignmentId), access);
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return getQuizPackage(quizId, access).quiz(); }

    /** Authorization runs on the small queries; the Quiz row is loaded only for the legacy fallback or to tell 404 from 403. */
    private QuizPackageResponse learnerPackage(String tenant, String quizId, String assignmentId, UserAccessContext access) {
        Student learner = policy.learner(tenant, access.userId()).orElse(null);
        if (assignmentId == null) {
            if (!policy.isAssignedQuiz(tenant, quizId, learner, access.userId())) throw notAssigned(tenant, quizId);
            QuizPackageResponse published = packages.activePackage(tenant, quizId).orElse(null);
            return published != null ? learnerView(published, false) : legacy(requireQuiz(tenant, quizId));
        }
        Assignment assignment = policy.assignment(tenant, assignmentId);
        var assigned = policy.assignedQuiz(assignment, quizId).orElseThrow(() -> notAssigned(tenant, quizId));
        // Same visibility + recipient rule as before (one exists query instead of find + exists + per-assignment lookups).
        if (!policy.isAssignedQuiz(tenant, quizId, assignment.getId(), learner, access.userId())) throw notAssigned(tenant, quizId);
        QuizPackageResponse published = (assigned.quizVersionId() == null
                ? packages.activePackage(tenant, quizId)
                : packages.packageForVersion(tenant, quizId, assigned.quizVersionId())).orElse(null);
        if (published == null) return legacy(requireQuiz(tenant, quizId));
        return learnerView(published, assignment.isOfflineAllowed());
    }

    private Quiz requireQuiz(String tenant, String quizId) {
        return quizzes.findByTenantIdAndId(tenant, quizId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("quiz not found"));
    }

    /** A missing quiz stays 404 for every caller; the lookup only runs on the denial path. */
    private RuntimeException notAssigned(String tenant, String quizId) {
        requireQuiz(tenant, quizId);
        return new ForbiddenException("quiz is not assigned to the authenticated learner");
    }

    /**
     * Practice (non-test) quizzes grade and explain each answer in the browser, so they need the key; the final
     * score is still recomputed on the server at submit. Test-mode quizzes only get the key when the assignment
     * explicitly allows offline grading.
     */
    private QuizPackageResponse learnerView(QuizPackageResponse published, boolean offlineAllowed) {
        return offlineAllowed || isPractice(published) ? published : QuizRuntimePackageService.withoutAnswerKey(published);
    }

    /** Mirrors the player: anything that is not explicitly "test"/"testing" runs as practice. */
    static boolean isPractice(QuizPackageResponse published) {
        Object mode = null;
        Map<String, Object> settings = published.quiz() == null ? null : published.quiz().settings();
        if (settings != null) {
            Object nested = settings.get("settings");
            Map<?, ?> source = nested instanceof Map<?, ?> m ? m : settings;
            mode = source.get("mode");
        }
        if (mode == null && published.quiz() != null && published.quiz().quiz() != null) mode = published.quiz().quiz().kind();
        String value = mode == null ? "" : mode.toString().trim().toLowerCase();
        return !value.equals("test") && !value.equals("testing");
    }

    private QuizPackageResponse legacy(Quiz quiz) {
        QuizResponse response = new QuizResponse(quiz.getId(),
                quiz.getScope() == null ? new ContentScope("global", null) : new ContentScope(quiz.getScope().getType(), quiz.getScope().getSchoolId()),
                quiz.getTitle(), quiz.getKind(), quiz.getSubjectId(), quiz.getLevelId(), list(quiz.getTopicIds()),
                list(quiz.getQuestionIds()), quiz.getStatus(), quiz.getVersion() == null ? 1 : quiz.getVersion(),
                quiz.getPackageHash(), quiz.getCreatedAt(), quiz.getUpdatedAt());
        QuizDetailResponse detail = new QuizDetailResponse(response, list(quiz.getSlides()), map(quiz.getSettings()),
                map(quiz.getResult()), map(quiz.getTheme()));
        return new QuizPackageResponse(response.version(), response.packageHash(), response.packageHash(), "local", "async", detail, Map.of(), null);
    }

    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
