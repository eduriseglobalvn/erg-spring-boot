package com.erg.ergspringboot.lcms.internal.readmodel;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.EffectivePermissionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuizOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.RoleOptionResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.ScopeOptionResponse;
import com.erg.ergspringboot.lcms.LcmsReadPort;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.UserAccessRowProjection;
import com.erg.ergspringboot.users.persistence.UserEffectivePermissionProjection;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumLevelRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumTopicRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.projection.QuizDraftSettingsProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.curriculum.persistence.projection.QuestionBankOptionProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuestionProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuizOptionProjection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionUsageCountProjection;
import com.erg.ergspringboot.lcms.persistence.LcmsAccessReadRepository;
import java.time.OffsetDateTime;
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
    private static final int DEFAULT_QUIZ_ROWS = 200;
    private static final int MAX_QUIZ_ROWS = 500;

    private final QuestionRepository questionRepository;
    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LcmsAccessReadRepository accessReads;
    private final QuizDraftRepository quizDraftRepository;

    public LcmsWorkspaceReadService(
            QuestionRepository questionRepository,
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            LcmsAccessReadRepository accessReads,
            QuizDraftRepository quizDraftRepository
    ) {
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accessReads = accessReads;
        this.quizDraftRepository = quizDraftRepository;
    }

    public QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId, String status,
            String quizKind, Boolean includeQuestions, Integer page, Integer size
    ) {
        return questionBankWorkspace(tenantId, search, subjectId, levelId, topicId, status, quizKind, includeQuestions, page,
                size, LcmsReadPort.QuestionBankReadOptions.ALL);
    }

    public QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId, String status,
            String quizKind, Boolean includeQuestions, Integer page, Integer size, LcmsReadPort.QuestionBankReadOptions options
    ) {
        return questionBankWorkspace(tenantId, search, subjectId, levelId, topicId, status, quizKind, includeQuestions, page, size,
                options, null);
    }

    /** {@code viewerUserId} is set for a teacher (their list hides other teachers' quizzes) and is part of the cache key. */
    @Cacheable(
            cacheNames = "lcmsQuestionBankWorkspace",
            key = "{#tenantId, #search, #subjectId, #levelId, #topicId, #status, #quizKind, #includeQuestions, #page, #size,"
                    + " #options, #viewerUserId}"
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
            Integer size,
            LcmsReadPort.QuestionBankReadOptions options,
            String viewerUserId
    ) {
        LcmsReadPort.QuestionBankReadOptions selected = options == null ? LcmsReadPort.QuestionBankReadOptions.ALL : options;
        int quizLimit = quizLimit(selected);
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
        Map<String, Integer> topicQuestionCounts = topicQuestionCounts(normalizedTenantId, topicOptions);
        Page<QuestionBankQuestionProjection> questions = shouldIncludeQuestions
                ? questionPage(
                        normalizedTenantId,
                        normalizedSearch,
                        normalizedSubjectId,
                        normalizedLevelId,
                        normalizedTopicId,
                        normalizedStatus,
                        questionPageRequest,
                        selected.runtimeQuestion()
                )
                : new org.springframework.data.domain.PageImpl<>(List.of(), questionPageRequest, 0);
        Map<String, Long> usageCounts = shouldIncludeQuestions
                ? questionUsageCounts(normalizedTenantId, questions.getContent())
                : Map.of();
        List<QuestionBankQuizOptionProjection> quizRows = selected.quizzes()
                ? quizOptions(normalizedTenantId, normalizedSearch, normalizedSubjectId, normalizedLevelId, normalizedTopicId,
                        normalizedStatus, normalizedQuizKind, viewerUserId, PageRequest.of(0, quizLimit + 1))
                : List.of();
        boolean quizzesTruncated = quizRows.size() > quizLimit;
        return new QuestionBankWorkspaceResponse(
                normalizedTenantId,
                LcmsWorkspaceResponseMapper.toQuestionPage(questions, usageCounts, selected.runtimeQuestion(),
                        selected.fromQuizEditor() ? quizEditorSources(normalizedTenantId, questions.getContent()) : null),
                subjectOptions.stream()
                        .map(option -> LcmsWorkspaceResponseMapper.toQuestionBankOption(option, List.of()))
                        .toList(),
                levelOptions.stream()
                        .map(option -> LcmsWorkspaceResponseMapper.toQuestionBankOption(option, topicIdsByLevelId.getOrDefault(option.id(), List.of())))
                        .toList(),
                topicOptions.stream()
                        .map(option -> LcmsWorkspaceResponseMapper.toQuestionBankOption(option, List.of(), topicQuestionCounts.getOrDefault(option.id(), 0)))
                        .toList(),
                quizOptionResponses(normalizedTenantId, quizzesTruncated ? quizRows.subList(0, quizLimit) : quizRows),
                quizzesTruncated
        );
    }

    private static int quizLimit(LcmsReadPort.QuestionBankReadOptions options) {
        return Math.max(1, Math.min(options.quizLimit() == null ? DEFAULT_QUIZ_ROWS : options.quizLimit(), MAX_QUIZ_ROWS));
    }

    private Map<String, Integer> topicQuestionCounts(String tenantId, List<QuestionBankOptionProjection> topics) {
        List<String> topicIds = topics.stream().map(QuestionBankOptionProjection::id).toList();
        if (topicIds.isEmpty()) return Map.of();
        return questionRepository.countActiveByTopic(tenantId, topicIds).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    private Page<QuestionBankQuestionProjection> questionPage(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            PageRequest pageRequest,
            boolean withContent
    ) {
        if (search == null) {
            return withContent
                    ? questionRepository.findQuestionBankQuestionsWithoutSearch(tenantId, subjectId, levelId, topicId, status, pageRequest)
                    : questionRepository.findQuestionBankQuestionsWithoutSearchSlim(tenantId, subjectId, levelId, topicId, status, pageRequest);
        }
        String folded = com.erg.ergspringboot.lcms.persistence.quiz.bank.VietnameseFold.fold(search);
        if (!withContent) {
            return questionRepository.findQuestionBankQuestionsSlim(tenantId, folded, subjectId, levelId, topicId, status, pageRequest);
        }
        return questionRepository.findQuestionBankQuestions(
                tenantId,
                folded,
                subjectId,
                levelId,
                topicId,
                status,
                pageRequest
        );
    }

    /** One query for every listed quiz's draft settings, so the time column shows the real limit. */
    private List<QuestionBankQuizOptionResponse> quizOptionResponses(
            String tenantId,
            List<QuestionBankQuizOptionProjection> quizzes
    ) {
        Map<String, QuizDraftSettingsProjection> latestDraftByQuizId = quizzes.isEmpty()
                ? Map.of()
                : quizDraftRepository.findSettingsByTenantIdAndQuizIds(
                                tenantId,
                                quizzes.stream().map(QuestionBankQuizOptionProjection::id).toList()
                        ).stream()
                        .filter(draft -> draft.settings() != null)
                        .collect(Collectors.toMap(
                                QuizDraftSettingsProjection::quizId,
                                draft -> draft,
                                (left, right) -> isAfter(right.updatedAt(), left.updatedAt()) ? right : left
                        ));
        return quizzes.stream()
                .map(quiz -> {
                    QuizDraftSettingsProjection draft = latestDraftByQuizId.get(quiz.id());
                    return LcmsWorkspaceResponseMapper.toQuizOption(quiz, draft == null ? null : draft.settings());
                })
                .toList();
    }

    private static boolean isAfter(OffsetDateTime candidate, OffsetDateTime current) {
        return candidate != null && (current == null || candidate.isAfter(current));
    }

    private List<QuestionBankQuizOptionProjection> quizOptions(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            String quizKind,
            String viewerUserId,
            PageRequest limit
    ) {
        if (search == null && status == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutStatusAndSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    topicId,
                    quizKind,
                    viewerUserId,
                    limit
            );
        }
        if (search == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    topicId,
                    status,
                    quizKind,
                    viewerUserId,
                    limit
            );
        }
        if (status == null) {
            return quizRepository.findQuestionBankQuizOptionsWithoutStatus(
                    tenantId,
                    search,
                    subjectId,
                    levelId,
                    topicId,
                    quizKind,
                    viewerUserId,
                    limit
            );
        }
        return quizRepository.findQuestionBankQuizOptions(
                tenantId,
                search,
                subjectId,
                levelId,
                topicId,
                status,
                quizKind,
                viewerUserId,
                limit
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
        Page<UserAccessRowProjection> users = accessReads.findRows(
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
                LcmsWorkspaceResponseMapper.toUserAccessPage(users, effectivePermissions),
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

    private List<com.erg.ergspringboot.users.persistence.authorization.projection.RoleOptionProjection> boundedRoleOptions() {
        PageRequest limit = PageRequest.of(0, MAX_OPTION_ROWS);
        List<com.erg.ergspringboot.users.persistence.authorization.projection.RoleOptionProjection> bounded =
                roleRepository.findAccessManagementRoleOptions(limit);
        return bounded == null ? roleRepository.findAccessManagementRoleOptions() : bounded;
    }

    private List<com.erg.ergspringboot.users.persistence.organization.projection.ScopeOptionProjection> boundedScopeOptions(
            String tenantId) {
        return accessReads.findScopeOptions(tenantId, PageRequest.of(0, MAX_OPTION_ROWS));
    }

    private Map<String, LcmsWorkspaceResponseMapper.QuizEditorSource> quizEditorSources(String tenantId, List<QuestionBankQuestionProjection> questions) {
        List<String> ids = questions.stream().map(QuestionBankQuestionProjection::id).toList();
        if (ids.isEmpty()) return Map.of();
        return questionRepository.findQuizEditorSources(tenantId, ids).stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> new LcmsWorkspaceResponseMapper.QuizEditorSource((String) row[1], (String) row[2]),
                (first, second) -> first));
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
