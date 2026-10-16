package com.erg.ergspringboot.lms.internal.resource;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsQuizService;
import com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Read-only quiz resource boundary; published packages are preferred over legacy embedded blobs. */
@Service
public class LmsResourceQueryUseCase {
    private final LmsQuizService quizzes;
    private final QuizRuntimePackageService packages;

    public LmsResourceQueryUseCase(LmsQuizService quizzes, QuizRuntimePackageService packages) {
        this.quizzes = quizzes; this.packages = packages;
    }

    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        Quiz quiz = quizzes.findByTenantIdAndId(tenant(access.tenantId()), quizId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("quiz not found"));
        QuizPackageResponse published = packages.activePackage(tenant(access.tenantId()), quizId).orElse(null);
        if (published != null) return published;
        QuizResponse response = new QuizResponse(quiz.getId(),
                quiz.getScope() == null ? new ContentScope("global", null) : new ContentScope(quiz.getScope().getType(), quiz.getScope().getSchoolId()),
                quiz.getTitle(), quiz.getKind(), quiz.getSubjectId(), quiz.getLevelId(), list(quiz.getTopicIds()),
                list(quiz.getQuestionIds()), quiz.getStatus(), quiz.getVersion() == null ? 1 : quiz.getVersion(),
                quiz.getPackageHash(), quiz.getCreatedAt(), quiz.getUpdatedAt());
        QuizDetailResponse detail = new QuizDetailResponse(response, list(quiz.getSlides()), map(quiz.getSettings()),
                map(quiz.getResult()), map(quiz.getTheme()));
        return new QuizPackageResponse(response.version(), response.packageHash(), response.packageHash(), "local", "async", detail, Map.of());
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) { return getQuizPackage(quizId, access).quiz(); }
    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
