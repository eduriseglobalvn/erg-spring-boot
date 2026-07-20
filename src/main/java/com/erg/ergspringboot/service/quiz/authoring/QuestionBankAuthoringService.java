package com.erg.ergspringboot.service.quiz.authoring;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.ContentScope;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.lms.QuestionChoice;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuestionTypeRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager")
public class QuestionBankAuthoringService {

    private static final int SCHEMA_VERSION = 1;

    private final QuestionRepository questionRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final AssessmentCurriculumBridge assessmentCurriculumBridge;

    public QuestionBankAuthoringService(
            QuestionRepository questionRepository,
            QuestionTypeRepository questionTypeRepository,
            AssessmentCurriculumBridge assessmentCurriculumBridge
    ) {
        this.questionRepository = questionRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.assessmentCurriculumBridge = assessmentCurriculumBridge;
    }

    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    public SaveQuestionResponse create(String tenantId, String userId, SaveQuestionRequest request) {
        validateRequired(tenantId, request);
        Question question = new Question();
        question.setTenantId(tenantId);
        question.setOwnerUserId(blankToNull(userId));
        question.setSchemaVersion(SCHEMA_VERSION);
        question.setMasteryRate(0.0d);
        question.setUsageCount(0);
        question.setSchoolsUsing(0);
        apply(question, request);
        Question saved = questionRepository.save(question);
        return new SaveQuestionResponse(saved.getId(), saved.getStatus());
    }

    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    public SaveQuestionResponse update(String tenantId, String questionId, SaveQuestionRequest request) {
        validateRequired(tenantId, request);
        Question question = questionRepository.findByIdAndTenantId(questionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("question not found"));
        apply(question, request);
        Question saved = questionRepository.save(question);
        return new SaveQuestionResponse(saved.getId(), saved.getStatus());
    }

    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    public DeleteQuestionResponse archive(String tenantId, String questionId) {
        Question question = questionRepository.findByIdAndTenantId(questionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("question not found"));
        if (question.getArchivedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            question.setArchivedAt(now);
            question.setDeletedAt(now);
            question.setStatus("archived");
            questionRepository.save(question);
        }
        return new DeleteQuestionResponse(question.getId(), true);
    }

    private void apply(Question question, SaveQuestionRequest request) {
        question.setScope(scope(request));
        question.setSubjectId(required(request.subjectId(), "subjectId is required"));
        question.setLevelId(required(request.levelId(), "levelId is required"));
        question.setTopicId(required(request.topicId(), "topicId is required"));
        question.setCategoryId(blankToNull(request.categoryId()) == null ? request.topicId().trim() : request.categoryId().trim());
        question.setType(requireQuestionType(request.type()));
        question.setStem(required(request.stem(), "stem is required"));
        question.setObjective(blankToNull(request.objective()));
        question.setDifficulty(normalizeDifficulty(request.difficulty()));
        question.setStatus(normalizeStatus(request.status()));
        question.setTags(cleanTags(request.tags()));
        question.setChoices(cleanChoices(request.choices()));
        question.setAnswer(request.answer());
        question.setContent(request.content() == null ? Map.of() : request.content());
        question.setSettings(request.settings() == null ? Map.of() : request.settings());
        question.setMetadata(request.metadata() == null ? Map.of() : request.metadata());
        OffsetDateTime now = OffsetDateTime.now();
        question.setArchivedAt("archived".equals(question.getStatus()) ? now : null);
        question.setDeletedAt("deleted".equals(question.getStatus()) ? now : null);
    }

    private void validateRequired(String tenantId, SaveQuestionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body is required");
        }
        required(request.subjectId(), "subjectId is required");
        required(request.levelId(), "levelId is required");
        required(request.topicId(), "topicId is required");
        required(request.stem(), "stem is required");
        assessmentCurriculumBridge.validateQuestionPlacement(
                tenantId,
                request.subjectId(),
                request.levelId(),
                request.topicId()
        );
    }

    private ContentScope scope(SaveQuestionRequest request) {
        String scopeType = blankToNull(request.scopeType());
        if (scopeType == null || scopeType.equalsIgnoreCase("global")) {
            return new ContentScope("global", null);
        }
        if (scopeType.equalsIgnoreCase("center") || scopeType.equalsIgnoreCase("school")) {
            return new ContentScope("school", required(request.scopeId(), "scopeId is required for center scope"));
        }
        throw new BadRequestException("scopeType must be global or center");
    }

    private String normalizeType(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "multiple_choice" : normalized.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String requireQuestionType(String value) {
        String type = normalizeType(value);
        if (!questionTypeRepository.existsById(type)) {
            throw new BadRequestException("question type is not supported: " + type);
        }
        return type;
    }

    private String normalizeDifficulty(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return "core";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!List.of("core", "stretch", "challenge").contains(normalized)) {
            throw new BadRequestException("difficulty must be core, stretch, or challenge");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return "ready";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!List.of("ready", "reviewing", "pilot", "draft", "archived", "deleted").contains(normalized)) {
            throw new BadRequestException("status must be ready, reviewing, pilot, draft, archived, or deleted");
        }
        return normalized;
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<QuestionChoice> cleanChoices(List<QuestionChoice> choices) {
        if (choices == null) {
            return List.of();
        }
        return choices.stream()
                .filter(choice -> choice != null && choice.getLabel() != null && !choice.getLabel().isBlank())
                .map(choice -> new QuestionChoice(
                        blankToNull(choice.getId()),
                        choice.getLabel().trim(),
                        Boolean.TRUE.equals(choice.getCorrect())
                ))
                .toList();
    }

    private String required(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
