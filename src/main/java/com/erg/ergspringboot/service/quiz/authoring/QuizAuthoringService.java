package com.erg.ergspringboot.service.quiz.authoring;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.CreateQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.DeleteQuizResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quiz creation (spec §4.2). Creates the quiz identity row plus an empty mutable draft in one
 * transaction. Create is idempotent on {@code X-Idempotency-Key} so a retried POST returns the same
 * quiz/draft instead of duplicating.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class QuizAuthoringService {

    private static final String PORTAL_LCMS = "lcms";
    private static final String STATUS_DRAFT = "draft";
    private static final int SCHEMA_VERSION = 1;

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizQuestionLinkService quizQuestionLinkService;
    private final AssessmentCurriculumBridge assessmentCurriculumBridge;

    public QuizAuthoringService(
            UserRepository userRepository,
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizQuestionLinkService quizQuestionLinkService,
            AssessmentCurriculumBridge assessmentCurriculumBridge
    ) {
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizQuestionLinkService = quizQuestionLinkService;
        this.assessmentCurriculumBridge = assessmentCurriculumBridge;
    }

    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    public CreateQuizResponse create(String tenantId, String userId, String idempotencyKey, CreateQuizRequest request) {
        String title = request == null ? null : request.title();
        if (title == null || title.isBlank()) {
            throw new BadRequestException("title is required");
        }
        String authorUserId = requireAuthorUser(tenantId, userId);
        String key = blankToNull(idempotencyKey);
        if (key != null) {
            var existing = quizRepository.findByTenantIdAndIdempotencyKey(tenantId, key);
            if (existing.isPresent()) {
                Quiz quiz = existing.get();
                return new CreateQuizResponse(quiz.getId(), quiz.getLatestDraftId(), currentDocVersion(quiz, tenantId));
            }
        }
        assessmentCurriculumBridge.validateQuizPlacement(
                tenantId,
                request.subjectId(),
                request.levelId(),
                request.categoryId()
        );

        Quiz quiz = new Quiz();
        quiz.setTenantId(tenantId);
        quiz.setPortal(PORTAL_LCMS);
        quiz.setTitle(title.trim());
        quiz.setSubtitle(blankToNull(request.subtitle()));
        quiz.setKind(normalizedKind(request.kind()));
        quiz.setSubjectId(blankToNull(request.subjectId()));
        quiz.setLevelId(blankToNull(request.levelId()));
        quiz.setCategoryId(blankToNull(request.categoryId()));
        quiz.setQuestionIds(cleanQuestionIds(request.questionIds()));
        quiz.setStatus(STATUS_DRAFT);
        quiz.setVersion(0);
        quiz.setSchemaVersion(SCHEMA_VERSION);
        quiz.setOwnerUserId(authorUserId);
        quiz.setIdempotencyKey(key);
        Quiz savedQuiz = quizRepository.save(quiz);

        QuizDraft draft = new QuizDraft();
        draft.setQuizId(savedQuiz.getId());
        draft.setTenantId(tenantId);
        draft.setSettings(Map.of());
        draft.setPlayerTemplate(Map.of());
        draft.setTree(java.util.List.of());
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setDocVersion(0L);
        draft.setUpdatedBy(authorUserId);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        savedQuiz.setLatestDraftId(savedDraft.getId());
        savedQuiz.setQuestionIds(quizQuestionLinkService.syncManualQuestionIds(tenantId, savedQuiz.getId(), savedQuiz.getQuestionIds()));
        quizRepository.save(savedQuiz);

        return new CreateQuizResponse(savedQuiz.getId(), savedDraft.getId(), savedDraft.getDocVersion());
    }

    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    public DeleteQuizResponse delete(String tenantId, String quizId) {
        Quiz quiz = quizRepository.findByIdAndTenantId(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));
        if (quiz.getDeletedAt() == null) {
            quiz.setDeletedAt(OffsetDateTime.now());
            quiz.setStatus("deleted");
            quizRepository.save(quiz);
        }
        return new DeleteQuizResponse(quiz.getId(), true);
    }

    private long currentDocVersion(Quiz quiz, String tenantId) {
        if (quiz.getLatestDraftId() == null) {
            return 0L;
        }
        return quizDraftRepository.findByIdAndTenantId(quiz.getLatestDraftId(), tenantId)
                .map(QuizDraft::getDocVersion)
                .orElse(0L);
    }

    private String normalizedKind(String kind) {
        String value = blankToNull(kind);
        if (value == null) {
            return "train";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.equals("train") && !normalized.equals("test")) {
            throw new BadRequestException("kind must be 'train' or 'test'");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireAuthorUser(String tenantId, String userId) {
        String authorUserId = blankToNull(userId);
        if (authorUserId == null) {
            throw new BadRequestException("author user is required");
        }
        if (userRepository.findByIdAndTenantId(authorUserId, tenantId).isEmpty()) {
            throw new BadRequestException("author user not found in tenant");
        }
        return authorUserId;
    }

    private List<String> cleanQuestionIds(List<String> questionIds) {
        if (questionIds == null) {
            return List.of();
        }
        return questionIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
