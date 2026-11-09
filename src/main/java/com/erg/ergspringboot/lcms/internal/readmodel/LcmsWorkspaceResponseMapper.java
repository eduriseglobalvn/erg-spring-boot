package com.erg.ergspringboot.lcms.internal.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.EffectivePermissionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankChoiceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuestionRowResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuizOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.UserAccessRowResponse;
import com.erg.ergspringboot.curriculum.persistence.projection.QuestionBankOptionProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuestionProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuizOptionProjection;
import com.erg.ergspringboot.users.persistence.UserAccessRowProjection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import tools.jackson.databind.ObjectMapper;

/** Maps LCMS workspace read projections to the published workspace response rows. */
final class LcmsWorkspaceResponseMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LcmsWorkspaceResponseMapper() {
    }

    private static String runtimeQuestionJson(Map<String, Object> content) {
        Object runtime = content == null ? null : content.get("runtimeQuestion");
        return runtime == null ? null : JSON.writeValueAsString(runtime);
    }

    static PageResponse<QuestionBankQuestionRowResponse> toQuestionPage(
            Page<QuestionBankQuestionProjection> questions,
            Map<String, Long> usageCounts
    ) {
        List<QuestionBankQuestionRowResponse> items = questions.getContent().stream()
                .map(question -> new QuestionBankQuestionRowResponse(
                        question.id(),
                        scopeType(question),
                        scopeId(question),
                        scopeLabel(question),
                        question.subjectId(),
                        blankToDefault(question.subjectLabel(), question.subjectId()),
                        question.levelId(),
                        blankToDefault(question.levelLabel(), question.levelId()),
                        question.topicId(),
                        blankToDefault(question.topicLabel(), question.topicId()),
                        blankToDefault(question.levelLabel(), question.subjectLabel()),
                        question.title(),
                        question.objective(),
                        question.type(),
                        question.difficulty(),
                        question.status(),
                        question.tags() == null ? List.of() : question.tags(),
                        question.choices() == null ? List.of() : question.choices().stream()
                                .map(choice -> new QuestionBankChoiceResponse(
                                        choice.getId(),
                                        choice.getLabel(),
                                        Boolean.TRUE.equals(choice.getCorrect())
                                ))
                                .toList(),
                        question.answer() == null ? null : String.valueOf(question.answer()),
                        question.masteryRate(),
                        usageCounts.getOrDefault(question.id(), question.usageCount() == null ? 0L : question.usageCount().longValue()),
                        question.schoolsUsing(),
                        question.updatedAt() == null ? null : question.updatedAt().toString(),
                        recommendedCluster(question),
                        null,
                        question.updatedAt() == null ? null : question.updatedAt().toString(),
                        runtimeQuestionJson(question.content())
                ))
                .toList();
        return new PageResponse<>(
                items,
                questions.getNumber(),
                questions.getSize(),
                questions.getTotalElements(),
                questions.getTotalPages(),
                questions.hasNext(),
                questions.hasPrevious()
        );
    }

    static PageResponse<UserAccessRowResponse> toUserAccessPage(
            Page<UserAccessRowProjection> users,
            Map<String, List<EffectivePermissionResponse>> effectivePermissions
    ) {
        List<UserAccessRowResponse> items = users.getContent().stream()
                .map(user -> new UserAccessRowResponse(
                        user.userId(),
                        user.fullName(),
                        user.email(),
                        user.status(),
                        user.roleCount() == null ? 0L : user.roleCount(),
                        user.scopeCount() == null ? 0L : user.scopeCount(),
                        effectivePermissions.getOrDefault(user.userId(), List.of())
                ))
                .toList();
        return new PageResponse<>(
                items,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.hasNext(),
                users.hasPrevious()
        );
    }

    static QuestionBankOptionResponse toQuestionBankOption(QuestionBankOptionProjection option, List<String> categoryIds) {
        return toQuestionBankOption(option, categoryIds, null);
    }

    static QuestionBankOptionResponse toQuestionBankOption(QuestionBankOptionProjection option, List<String> categoryIds, Integer questionCount) {
        return new QuestionBankOptionResponse(
                option.id(),
                option.label(),
                option.subjectId(),
                option.levelId(),
                blankToDefault(option.description(), option.label()),
                "Toàn công ty",
                categoryIds,
                questionCount
        );
    }

    private static String scopeType(QuestionBankQuestionProjection question) {
        return question.scope() == null || blankToNull(question.scope().getType()) == null ? "global" : question.scope().getType().trim();
    }

    private static String scopeId(QuestionBankQuestionProjection question) {
        return question.scope() == null ? null : blankToNull(question.scope().getSchoolId());
    }

    private static String scopeLabel(QuestionBankQuestionProjection question) {
        String type = scopeType(question);
        String schoolId = scopeId(question);
        if (schoolId != null) {
            return schoolId;
        }
        return "global".equalsIgnoreCase(type) ? "Toàn công ty" : type;
    }

    private static String recommendedCluster(QuestionBankQuestionProjection question) {
        String scopeId = scopeId(question);
        return scopeId == null ? "ERG" : scopeId;
    }

    static QuestionBankQuizOptionResponse toQuizOption(QuestionBankQuizOptionProjection option, Map<String, Object> draftSettings) {
        List<String> questionIds = cleanStrings(option.questionIds());
        long published = option.publishedQuestionCount() == null ? 0 : option.publishedQuestionCount();
        int questionCount = (int) Math.max(questionIds.size(), published);
        return new QuestionBankQuizOptionResponse(
                option.id(),
                blankToDefault(option.title(), option.id()),
                option.status(),
                option.kind(),
                option.subjectId(),
                option.levelId(),
                option.categoryId(),
                questionIds,
                questionCount,
                durationLabel(draftSettings, questionCount, option.kind()),
                "Toàn công ty",
                sourceMode(option.kind()),
                blankToDefault(option.ownerUserId(), "Academic team"),
                option.updatedAt() == null ? null : option.updatedAt().toString()
        );
    }

    /** The draft's own time limit; the per-question estimate only for a quiz without a draft. */
    static String durationLabel(Map<String, Object> draftSettings, int questionCount, String kind) {
        if (draftSettings != null && draftSettings.get("settings") instanceof Map<?, ?> settings) {
            int minutes = minutesOf(settings.get("timeLimit"));
            return Boolean.TRUE.equals(settings.get("enableTimeLimit")) && minutes > 0 ? minutes + " phút" : "Không giới hạn";
        }
        return durationLabel(questionCount, kind);
    }

    private static int minutesOf(Object timeLimit) {
        if (!(timeLimit instanceof String text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text.split(":", 2)[0].trim());
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static String durationLabel(int questionCount, String kind) {
        int minutesPerQuestion = "test".equalsIgnoreCase(blankToNull(kind)) ? 2 : 3;
        return Math.max(5, questionCount * minutesPerQuestion) + " phút";
    }

    private static String sourceMode(String kind) {
        return "test".equalsIgnoreCase(blankToNull(kind)) ? "manual" : "auto-random";
    }

    private static List<String> cleanStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
