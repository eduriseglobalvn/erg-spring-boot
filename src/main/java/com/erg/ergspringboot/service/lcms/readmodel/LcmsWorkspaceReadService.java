package com.erg.ergspringboot.service.lcms.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.EffectivePermissionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuestionRowResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankChoiceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuizOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.RoleOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.ScopeOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.UserAccessRowResponse;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.auth.user.projection.UserAccessRowProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizQuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuestionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionUsageCountProjection;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LcmsWorkspaceReadService {

    private static final String ACTIVE = "active";
    private static final int MAX_OPTION_ROWS = 500;

    private final QuestionRepository questionRepository;
    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAccessScopeRepository userAccessScopeRepository;

    public LcmsWorkspaceReadService(
            QuestionRepository questionRepository,
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserAccessScopeRepository userAccessScopeRepository
    ) {
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userAccessScopeRepository = userAccessScopeRepository;
    }

    @Cacheable(
            cacheNames = "lcmsQuestionBankWorkspace",
            key = "{#tenantId, #search, #subjectId, #levelId, #topicId, #status, #quizKind, #includeQuestions, #page, #size}"
    )
    public QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            String quizKind,
            Boolean includeQuestions,
            Integer page,
            Integer size
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        PageBounds pageBounds = pageBounds(page, size, 50);
        String normalizedSubjectId = blankToNull(subjectId);
        String normalizedLevelId = blankToNull(levelId);
        String normalizedTopicId = blankToNull(topicId);
        String normalizedStatus = normalized(status);
        String normalizedQuizKind = normalized(quizKind);
        String normalizedSearch = normalized(search);
        boolean shouldIncludeQuestions = includeQuestions == null || includeQuestions;
        PageRequest questionPageRequest = PageRequest.of(pageBounds.page(), pageBounds.size());
        PageRequest optionLimit = PageRequest.of(0, MAX_OPTION_ROWS);
        List<QuestionBankOptionProjection> subjectOptions = boundedSubjectOptions(normalizedTenantId, ACTIVE, optionLimit);
        List<QuestionBankOptionProjection> levelOptions = boundedLevelOptions(normalizedTenantId, normalizedSubjectId, ACTIVE, optionLimit);
        List<QuestionBankOptionProjection> topicOptions = boundedTopicOptions(normalizedTenantId, normalizedLevelId, ACTIVE, optionLimit);
        Map<String, List<String>> topicIdsByLevelId = topicOptions.stream()
                .filter(topic -> blankToNull(topic.levelId()) != null)
                .collect(Collectors.groupingBy(
                        QuestionBankOptionProjection::levelId,
                        Collectors.mapping(QuestionBankOptionProjection::id, Collectors.toList())
                ));
        Page<QuestionBankQuestionProjection> questions = shouldIncludeQuestions
                ? questionPage(
                        normalizedTenantId,
                        normalizedSearch,
                        normalizedSubjectId,
                        normalizedLevelId,
                        normalizedTopicId,
                        normalizedStatus,
                        questionPageRequest
                )
                : new org.springframework.data.domain.PageImpl<>(List.of(), questionPageRequest, 0);
        Map<String, Long> usageCounts = shouldIncludeQuestions
                ? questionUsageCounts(normalizedTenantId, questions.getContent())
                : Map.of();
        return new QuestionBankWorkspaceResponse(
                normalizedTenantId,
                toQuestionPage(questions, usageCounts),
                subjectOptions.stream()
                        .map(option -> toQuestionBankOption(option, List.of()))
                        .toList(),
                levelOptions.stream()
                        .map(option -> toQuestionBankOption(option, topicIdsByLevelId.getOrDefault(option.id(), List.of())))
                        .toList(),
                topicOptions.stream()
                        .map(option -> toQuestionBankOption(option, List.of()))
                        .toList(),
                quizOptions(
                        normalizedTenantId,
                        normalizedSearch,
                        normalizedSubjectId,
                        normalizedLevelId,
                        normalizedTopicId,
                        normalizedStatus,
                        normalizedQuizKind
                )
                        .stream()
                        .map(this::toQuizOption)
                        .toList()
        );
    }

    private Page<QuestionBankQuestionProjection> questionPage(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            PageRequest pageRequest
    ) {
        if (search == null) {
            return questionRepository.findQuestionBankQuestionsWithoutSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    topicId,
                    status,
                    pageRequest
            );
        }
        return questionRepository.findQuestionBankQuestions(
                tenantId,
                search,
                subjectId,
                levelId,
                topicId,
                status,
                pageRequest
        );
    }

    private List<QuestionBankQuizOptionProjection> quizOptions(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            String quizKind
    ) {
        if (search == null && status == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutStatusAndSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    topicId,
                    quizKind
            );
        }
        if (search == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    topicId,
                    status,
                    quizKind
            );
        }
        if (status == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutStatus(
                    tenantId,
                    search,
                    subjectId,
                    levelId,
                    topicId,
                    quizKind
            );
        }
        return quizRepository.findQuestionBankQuizOptions(
                tenantId,
                search,
                subjectId,
                levelId,
                topicId,
                status,
                quizKind
        );
    }

    public AccessManagementWorkspaceResponse accessManagementWorkspace(
            String tenantId,
            String search,
            String roleId,
            String status,
            Integer page,
            Integer size,
            boolean includeEffectivePermissions
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        PageBounds pageBounds = pageBounds(page, size, 50);
        Page<UserAccessRowProjection> users = userRepository.findAccessManagementRows(
                normalizedTenantId,
                blankToNull(search),
                blankToNull(roleId),
                normalized(status),
                PageRequest.of(pageBounds.page(), pageBounds.size())
        );
        Map<String, List<EffectivePermissionResponse>> effectivePermissions = includeEffectivePermissions
                ? effectivePermissions(users.getContent())
                : Map.of();
        return new AccessManagementWorkspaceResponse(
                normalizedTenantId,
                toUserAccessPage(users, effectivePermissions),
                boundedRoleOptions().stream()
                        .map(role -> new RoleOptionResponse(role.id(), role.name()))
                        .toList(),
                boundedScopeOptions(normalizedTenantId).stream()
                        .map(scope -> new ScopeOptionResponse(scope.id(), scope.label(), scope.kind()))
                        .toList(),
                includeEffectivePermissions
        );
    }

    private List<QuestionBankOptionProjection> boundedSubjectOptions(
            String tenantId, String status, PageRequest limit) {
        List<QuestionBankOptionProjection> bounded = subjectRepository.findAssessmentOptions(tenantId, status, limit);
        return bounded == null ? subjectRepository.findAssessmentOptions(tenantId, status) : bounded;
    }

    private List<QuestionBankOptionProjection> boundedLevelOptions(
            String tenantId, String subjectId, String status, PageRequest limit) {
        List<QuestionBankOptionProjection> bounded = levelRepository.findAssessmentOptions(tenantId, subjectId, status, limit);
        return bounded == null ? levelRepository.findAssessmentOptions(tenantId, subjectId, status) : bounded;
    }

    private List<QuestionBankOptionProjection> boundedTopicOptions(
            String tenantId, String levelId, String status, PageRequest limit) {
        List<QuestionBankOptionProjection> bounded = topicRepository.findAssessmentOptions(tenantId, levelId, status, limit);
        return bounded == null ? topicRepository.findAssessmentOptions(tenantId, levelId, status) : bounded;
    }

    private List<com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection> boundedRoleOptions() {
        PageRequest limit = PageRequest.of(0, MAX_OPTION_ROWS);
        List<com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection> bounded =
                roleRepository.findAccessManagementRoleOptions(limit);
        return bounded == null ? roleRepository.findAccessManagementRoleOptions() : bounded;
    }

    private List<com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection> boundedScopeOptions(
            String tenantId) {
        List<com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection> bounded =
                userAccessScopeRepository.findAccessManagementScopeOptions(tenantId, PageRequest.of(0, MAX_OPTION_ROWS));
        return bounded == null ? userAccessScopeRepository.findAccessManagementScopeOptions(tenantId) : bounded;
    }

    private PageResponse<QuestionBankQuestionRowResponse> toQuestionPage(
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
                        question.updatedAt() == null ? null : question.updatedAt().toString()
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

    private Map<String, Long> questionUsageCounts(String tenantId, List<QuestionBankQuestionProjection> questions) {
        List<String> questionIds = questions.stream()
                .map(QuestionBankQuestionProjection::id)
                .toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return quizQuestionRepository.countUsageByQuestionIds(tenantId, questionIds).stream()
                .collect(Collectors.toMap(
                        QuestionUsageCountProjection::getQuestionId,
                        usage -> usage.getUsageCount() == null ? 0L : usage.getUsageCount()
                ));
    }

    private PageResponse<UserAccessRowResponse> toUserAccessPage(
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

    private Map<String, List<EffectivePermissionResponse>> effectivePermissions(List<UserAccessRowProjection> users) {
        List<String> userIds = users.stream()
                .map(UserAccessRowProjection::userId)
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return Stream.concat(
                        userRepository.findRoleEffectivePermissionsByUserIds(userIds).stream(),
                        userRepository.findOverrideEffectivePermissionsByUserIds(userIds).stream()
                )
                .collect(Collectors.groupingBy(
                        UserEffectivePermissionProjection::userId,
                        Collectors.mapping(
                                permission -> new EffectivePermissionResponse(permission.permission(), permission.source()),
                                Collectors.toList()
                        )
                ));
    }

    private QuestionBankOptionResponse toQuestionBankOption(QuestionBankOptionProjection option, List<String> categoryIds) {
        return new QuestionBankOptionResponse(
                option.id(),
                option.label(),
                option.subjectId(),
                option.levelId(),
                blankToDefault(option.description(), option.label()),
                "Toan cong ty",
                categoryIds
        );
    }

    private String scopeType(QuestionBankQuestionProjection question) {
        return question.scope() == null || blankToNull(question.scope().getType()) == null ? "global" : question.scope().getType().trim();
    }

    private String scopeId(QuestionBankQuestionProjection question) {
        return question.scope() == null ? null : blankToNull(question.scope().getSchoolId());
    }

    private String scopeLabel(QuestionBankQuestionProjection question) {
        String type = scopeType(question);
        String schoolId = scopeId(question);
        if (schoolId != null) {
            return schoolId;
        }
        return "global".equalsIgnoreCase(type) ? "Toan cong ty" : type;
    }

    private String recommendedCluster(QuestionBankQuestionProjection question) {
        String scopeId = scopeId(question);
        return scopeId == null ? "ERG" : scopeId;
    }

    private QuestionBankQuizOptionResponse toQuizOption(QuestionBankQuizOptionProjection option) {
        List<String> questionIds = cleanStrings(option.questionIds());
        return new QuestionBankQuizOptionResponse(
                option.id(),
                blankToDefault(option.title(), option.id()),
                option.status(),
                option.kind(),
                option.subjectId(),
                option.levelId(),
                option.categoryId(),
                questionIds,
                questionIds.size(),
                durationLabel(questionIds.size(), option.kind()),
                "Toan cong ty",
                sourceMode(option.kind()),
                blankToDefault(option.ownerUserId(), "Academic team"),
                option.updatedAt() == null ? null : option.updatedAt().toString()
        );
    }

    private String durationLabel(int questionCount, String kind) {
        int minutesPerQuestion = "test".equalsIgnoreCase(blankToNull(kind)) ? 2 : 3;
        return Math.max(5, questionCount * minutesPerQuestion) + " phut";
    }

    private String sourceMode(String kind) {
        return "test".equalsIgnoreCase(blankToNull(kind)) ? "manual" : "auto-random";
    }

    private List<String> cleanStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private PageBounds pageBounds(Integer page, Integer size, int hardMax) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, hardMax);
        return new PageBounds(safePage, safeSize);
    }

    private String normalized(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PageBounds(int page, int size) {
    }
}
