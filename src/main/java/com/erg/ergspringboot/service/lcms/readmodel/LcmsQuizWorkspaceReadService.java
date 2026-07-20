package com.erg.ergspringboot.service.lcms.readmodel;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizSummaryPageResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizSummaryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionSummaryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.quiz.authoring.projection.QuizSummaryProjection;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for the LCMS quiz workspace + editor hydrate (spec §4.1). Projections only — never
 * materializes entity graphs — and clamps page size to the same hard max as the question-bank
 * workspace so the FE {@code GraphQlPage<T>} contract is identical. Draft blobs are serialized to
 * JSON strings for transport (no custom GraphQL scalar).
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LcmsQuizWorkspaceReadService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int HARD_MAX_PAGE_SIZE = 50;
    public static final int MAX_VERSION_HISTORY = 100;
    private static final String ACTIVE = "active";

    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    public LcmsQuizWorkspaceReadService(
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizVersionRepository quizVersionRepository,
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository,
            ObjectMapper objectMapper
    ) {
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizVersionRepository = quizVersionRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.objectMapper = objectMapper;
    }

    public QuizVersionHistoryResponse quizVersions(String tenantId, String quizId) {
        List<QuizVersion> versions = quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc(
                quizId, tenantId, PageRequest.of(0, MAX_VERSION_HISTORY + 1));
        if (versions.size() > MAX_VERSION_HISTORY) {
            throw new BadRequestException("quiz versions exceeds " + MAX_VERSION_HISTORY
                    + " rows; use the paged version-history API");
        }
        List<QuizVersionSummaryResponse> items = versions.stream()
                .map(this::toVersionSummary)
                .toList();
        return new QuizVersionHistoryResponse(quizId, items);
    }

    private QuizVersionSummaryResponse toVersionSummary(QuizVersion version) {
        return new QuizVersionSummaryResponse(
                version.getId(),
                version.getVersionLabel(),
                version.getVersionNumber() == null ? 0 : version.getVersionNumber(),
                version.getGradingMode(),
                version.getContentHash(),
                version.getPublishedBy(),
                version.getPublishedAt() == null ? null : version.getPublishedAt().toString(),
                Boolean.TRUE.equals(version.getIsActive())
        );
    }

    @Cacheable(
            cacheNames = "lcmsQuizWorkspace",
            key = "{#tenantId, #search, #subjectId, #levelId, #categoryId, #status, #kind, #page, #size}"
    )
    public QuizWorkspaceResponse quizWorkspace(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String categoryId,
            String status,
            String kind,
            Integer page,
            Integer size
    ) {
        Page<QuizSummaryProjection> quizPage = quizSummaries(
                tenantId,
                normalized(search),
                blankToNull(subjectId),
                blankToNull(levelId),
                blankToNull(categoryId),
                normalized(status),
                normalized(kind),
                PageRequest.of(safePage(page), safeSize(size))
        );

        List<QuizSummaryResponse> items = quizPage.getContent().stream()
                .map(this::toSummary)
                .toList();

        List<QuestionBankOptionProjection> subjectOptions = subjectRepository.findAssessmentOptions(tenantId, ACTIVE);
        List<QuestionBankOptionProjection> levelOptions = levelRepository.findAssessmentOptions(tenantId, blankToNull(subjectId), ACTIVE);
        List<QuestionBankOptionProjection> topicOptions = topicRepository.findAssessmentOptions(tenantId, blankToNull(levelId), ACTIVE);
        Map<String, List<String>> topicIdsByLevelId = topicOptions.stream()
                .filter(topic -> blankToNull(topic.levelId()) != null)
                .collect(Collectors.groupingBy(
                        QuestionBankOptionProjection::levelId,
                        Collectors.mapping(QuestionBankOptionProjection::id, Collectors.toList())
                ));

        return new QuizWorkspaceResponse(
                tenantId,
                new QuizSummaryPageResponse(
                        items,
                        quizPage.getNumber(),
                        quizPage.getSize(),
                        quizPage.getTotalElements(),
                        quizPage.getTotalPages(),
                        quizPage.hasNext(),
                        quizPage.hasPrevious()
                ),
                options(subjectOptions, Map.of()),
                options(levelOptions, topicIdsByLevelId),
                options(topicOptions, Map.of())
        );
    }

    public QuizDraftDocumentResponse quizDraft(String tenantId, String quizId) {
        QuizDraft draft = quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz draft not found"));
        return new QuizDraftDocumentResponse(
                draft.getQuizId(),
                draft.getId(),
                draft.getDocVersion() == null ? 0 : draft.getDocVersion().intValue(),
                null,
                draft.getUpdatedAt() == null ? null : draft.getUpdatedAt().toString(),
                toJson(draft.getSettings()),
                toJson(draft.getPlayerTemplate()),
                toJson(draft.getTree())
        );
    }

    private Page<QuizSummaryProjection> quizSummaries(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String categoryId,
            String status,
            String kind,
            PageRequest pageRequest
    ) {
        if (search == null) {
            return quizRepository.findQuizSummariesWithoutSearch(
                    tenantId,
                    subjectId,
                    levelId,
                    categoryId,
                    status,
                    kind,
                    pageRequest
            );
        }
        return quizRepository.findQuizSummaries(
                tenantId,
                search,
                subjectId,
                levelId,
                categoryId,
                status,
                kind,
                pageRequest
        );
    }

    private QuizSummaryResponse toSummary(QuizSummaryProjection projection) {
        return new QuizSummaryResponse(
                projection.id(),
                blankToDefault(projection.title(), projection.id()),
                projection.subtitle(),
                projection.kind(),
                projection.status(),
                projection.subjectId(),
                projection.levelId(),
                projection.categoryId(),
                null,
                cleanStrings(projection.questionIds()).size(),
                projection.updatedAt() == null ? null : projection.updatedAt().toString()
        );
    }

    private List<QuestionBankOptionResponse> options(List<QuestionBankOptionProjection> options, Map<String, List<String>> categoryIdsByOptionId) {
        return options.stream()
                .map(option -> new QuestionBankOptionResponse(
                        option.id(),
                        option.label(),
                        option.subjectId(),
                        option.levelId(),
                        blankToDefault(option.description(), option.label()),
                        "Toan cong ty",
                        categoryIdsByOptionId.getOrDefault(option.id(), List.of())
                ))
                .toList();
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

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, HARD_MAX_PAGE_SIZE);
    }

    private String normalized(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
