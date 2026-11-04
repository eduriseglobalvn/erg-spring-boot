package com.erg.ergspringboot.lms.internal.resource;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsCallerPermissions;
import com.erg.ergspringboot.lms.LmsStatePolicy;
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
        Quiz quiz = quizzes.findByTenantIdAndId(tenant, quizId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("quiz not found"));
        if (permissions.hasAny(AUTHORING_READ)) {
            QuizPackageResponse published = packages.activePackage(tenant, quizId).orElse(null);
            return published != null ? published : legacy(quiz);
        }
        return learnerPackage(tenant, quiz, blank(assignmentId), access);
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return getQuizPackage(quizId, access).quiz(); }

    private QuizPackageResponse learnerPackage(String tenant, Quiz quiz, String assignmentId, UserAccessContext access) {
        Student learner = policy.learner(tenant, access.userId()).orElse(null);
        if (assignmentId == null) {
            if (!policy.isAssignedQuiz(tenant, quiz.getId(), learner, access.userId())) {
                throw new ForbiddenException("quiz is not assigned to the authenticated learner");
            }
            QuizPackageResponse published = packages.activePackage(tenant, quiz.getId()).orElse(null);
            return published != null ? QuizRuntimePackageService.withoutAnswerKey(published) : legacy(quiz);
        }
        Assignment assignment = policy.assignment(tenant, assignmentId);
        var assigned = policy.assignedQuiz(assignment, quiz.getId())
                .orElseThrow(() -> new ForbiddenException("quiz is not assigned to the authenticated learner"));
        if (!LmsStatePolicy.assignmentIsVisible(assignment.getStatus())
                || !policy.isRecipient(assignment, learner, access.userId())) {
            throw new ForbiddenException("quiz is not assigned to the authenticated learner");
        }
        QuizPackageResponse published = (assigned.quizVersionId() == null
                ? packages.activePackage(tenant, quiz.getId())
                : packages.packageForVersion(tenant, quiz.getId(), assigned.quizVersionId())).orElse(null);
        if (published == null) return legacy(quiz);
        return assignment.isOfflineAllowed() ? published : QuizRuntimePackageService.withoutAnswerKey(published);
    }

    private QuizPackageResponse legacy(Quiz quiz) {
        QuizResponse response = new QuizResponse(quiz.getId(),
                quiz.getScope() == null ? new ContentScope("global", null) : new ContentScope(quiz.getScope().getType(), quiz.getScope().getSchoolId()),
                quiz.getTitle(), quiz.getKind(), quiz.getSubjectId(), quiz.getLevelId(), list(quiz.getTopicIds()),
                list(quiz.getQuestionIds()), quiz.getStatus(), quiz.getVersion() == null ? 1 : quiz.getVersion(),
                quiz.getPackageHash(), quiz.getCreatedAt(), quiz.getUpdatedAt());
        QuizDetailResponse detail = new QuizDetailResponse(response, list(quiz.getSlides()), map(quiz.getSettings()),
                map(quiz.getResult()), map(quiz.getTheme()));
        return new QuizPackageResponse(response.version(), response.packageHash(), response.packageHash(), "local", "async", detail, Map.of());
    }

    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
