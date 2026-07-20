package com.erg.ergspringboot.service.quiz.publish;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ForkResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.PublishResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationErrorResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationResultResponse;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.StaleDraftException;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizQuestionRepository;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionSection;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionSectionRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import com.erg.ergspringboot.service.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.service.quiz.publish.CompiledQuiz.CompiledSection;
import com.erg.ergspringboot.service.quiz.publish.QuizValidationService.ValidationError;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Publish/validate/fork orchestration (spec §2, §4.2). Publish is a single transaction: it optimistic-locks
 * the draft, compiles the runtime quiz + answer key (§3), inserts the immutable version + normalized
 * question set + signed package, flips the active version, and points the quiz at it. Idempotent on the
 * mandatory {@code X-Idempotency-Key} so a retried publish returns the same version instead of duplicating.
 */
@Service
public class QuizPublishService {

    private static final int SCHEMA_VERSION = 1;
    private static final String MODE_CLIENT_FIRST = "client-first";
    private static final String MODE_SERVER_AUTH = "server-authoritative";

    private final QuizRepository quizRepository;
    private final QuestionRepository bankQuestionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final QuizVersionSectionRepository sectionRepository;
    private final QuizVersionQuestionRepository versionQuestionRepository;
    private final QuizPackageRepository packageRepository;
    private final QuizCompileService compileService;
    private final QuizValidationService validationService;
    private final QuizPackageSigner packageSigner;
    private final AssessmentCurriculumBridge assessmentCurriculumBridge;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public QuizPublishService(
            QuizRepository quizRepository,
            QuestionRepository bankQuestionRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizDraftRepository quizDraftRepository,
            QuizVersionRepository quizVersionRepository,
            QuizVersionSectionRepository sectionRepository,
            QuizVersionQuestionRepository versionQuestionRepository,
            QuizPackageRepository packageRepository,
            QuizCompileService compileService,
            QuizValidationService validationService,
            QuizPackageSigner packageSigner,
            AssessmentCurriculumBridge assessmentCurriculumBridge,
            ObjectMapper objectMapper,
            CacheManager cacheManager
    ) {
        this.quizRepository = quizRepository;
        this.bankQuestionRepository = bankQuestionRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizVersionRepository = quizVersionRepository;
        this.sectionRepository = sectionRepository;
        this.versionQuestionRepository = versionQuestionRepository;
        this.packageRepository = packageRepository;
        this.compileService = compileService;
        this.validationService = validationService;
        this.packageSigner = packageSigner;
        this.assessmentCurriculumBridge = assessmentCurriculumBridge;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public ValidationResultResponse validate(String tenantId, String quizId) {
        QuizDraft draft = latestDraft(tenantId, quizId);
        CompiledQuiz compiled = compileWithSettings(tenantId, quizId, draft);
        List<ValidationErrorResponse> errors = validationService.validate(compiled).stream()
                .map(this::toErrorResponse)
                .toList();
        return new ValidationResultResponse(errors.isEmpty(), errors);
    }

    @Transactional(value = "postgresTransactionManager")
    public PublishResponse publish(String tenantId, String quizId, String userId, String idempotencyKey, PublishRequest request) {
        String key = blankToNull(idempotencyKey);
        if (key == null) {
            throw new BadRequestException("X-Idempotency-Key is required to publish");
        }
        Quiz quiz = quizRepository.findByIdAndTenantId(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));

        // Idempotent replay: a version already stamped with this publish key returns unchanged.
        QuizVersion existing = quizVersionRepository
                .findByQuizIdAndTenantIdOrderByVersionNumberDesc(quizId, tenantId).stream()
                .filter(version -> key.equals(version.getIdempotencyKey()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            QuizPackage pkg = packageRepository.findByQuizVersionId(existing.getId()).orElse(null);
            return new PublishResponse(
                    existing.getId(), existing.getVersionLabel(), existing.getVersionNumber(),
                    pkg == null ? null : pkg.getId(), existing.getContentHash());
        }

        QuizDraft draft = latestDraft(tenantId, quizId);
        assertDocVersion(draft, request == null ? null : request.docVersion());

        CompiledQuiz compiled = compileWithSettings(tenantId, quizId, draft);
        List<ValidationError> errors = validationService.validate(compiled);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String gradingMode = normalizeGradingMode(request == null ? null : request.gradingMode());
        int versionNumber = quizVersionRepository
                .findTopByQuizIdAndTenantIdOrderByVersionNumberDesc(quizId, tenantId)
                .map(QuizVersion::getVersionNumber)
                .orElse(0) + 1;
        String versionLabel = resolveLabel(request == null ? null : request.versionLabel(), versionNumber);

        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("runtimeQuiz", compiled.runtimeQuiz());
        hashPayload.put("answerKey", compiled.answerKey());
        String contentHash = packageSigner.contentHash(canonicalJson(hashPayload));

        // Deactivate the previous active version, then insert this one active.
        quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .ifPresent(active -> {
                    active.setIsActive(false);
                    quizVersionRepository.save(active);
                });

        QuizVersion version = new QuizVersion();
        version.setQuizId(quizId);
        version.setTenantId(tenantId);
        version.setVersionLabel(versionLabel);
        version.setVersionNumber(versionNumber);
        version.setAuthoringSnapshot(snapshot(draft, assessmentCurriculumBridge.snapshotForQuiz(tenantId, quiz)));
        version.setRuntimeQuiz(compiled.runtimeQuiz());
        version.setAnswerKey(compiled.answerKey());
        version.setGradingMode(gradingMode);
        version.setSchemaVersion(SCHEMA_VERSION);
        version.setContentHash(contentHash);
        version.setPublishedBy(userId);
        version.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        version.setIsActive(true);
        version.setIdempotencyKey(key);
        QuizVersion savedVersion = quizVersionRepository.save(version);

        persistSections(tenantId, savedVersion.getId(), compiled);

        QuizPackage pkg = buildPackage(tenantId, quiz, savedVersion, compiled, gradingMode, contentHash);
        QuizPackage savedPackage = packageRepository.save(pkg);

        quiz.setCurrentVersionId(savedVersion.getId());
        quiz.setStatus("published");
        quiz.setVersion(versionNumber);
        quiz.setPackageHash(contentHash);
        quiz.setPublishedAt(savedVersion.getPublishedAt());
        quizRepository.save(quiz);

        evictQuizCachesAfterCommit(tenantId, quizId);

        return new PublishResponse(
                savedVersion.getId(), versionLabel, versionNumber, savedPackage.getId(), contentHash);
    }

    @Transactional(value = "postgresTransactionManager")
    public ForkResponse fork(String tenantId, String quizId, String versionId, String userId) {
        QuizVersion version = quizVersionRepository.findByIdAndTenantId(versionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz version not found"));
        if (!quizId.equals(version.getQuizId())) {
            throw new BadRequestException("version does not belong to quiz");
        }
        Map<String, Object> snapshot = version.getAuthoringSnapshot() == null ? Map.of() : version.getAuthoringSnapshot();

        QuizDraft draft = new QuizDraft();
        draft.setQuizId(quizId);
        draft.setTenantId(tenantId);
        draft.setSettings(asMap(snapshot.get("settings")));
        draft.setPlayerTemplate(asMap(snapshot.get("playerTemplate")));
        draft.setTree(snapshot.get("tree"));
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setDocVersion(0L);
        draft.setBaseVersionId(versionId);
        draft.setUpdatedBy(userId);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        quizRepository.findByIdAndTenantId(quizId, tenantId).ifPresent(quiz -> {
            quiz.setLatestDraftId(savedDraft.getId());
            quizRepository.save(quiz);
        });
        evictQuizCachesAfterCommit(tenantId, quizId);
        return new ForkResponse(savedDraft.getId(), 0L, version.getVersionLabel());
    }

    private void persistSections(String tenantId, String versionId, CompiledQuiz compiled) {
        Map<String, String> sectionIdMap = new LinkedHashMap<>();
        for (CompiledSection section : compiled.sections()) {
            QuizVersionSection entity = new QuizVersionSection();
            entity.setQuizVersionId(versionId);
            entity.setTenantId(tenantId);
            entity.setTitle(section.title());
            entity.setOrderIndex(section.orderIndex());
            QuizVersionSection saved = sectionRepository.save(entity);
            sectionIdMap.put(section.sectionId(), saved.getId());
        }
        for (CompiledQuestion question : compiled.questions()) {
            QuizVersionQuestion entity = new QuizVersionQuestion();
            entity.setQuizVersionId(versionId);
            entity.setSectionId(sectionIdMap.get(question.sectionId()));
            entity.setTenantId(tenantId);
            entity.setRuntimeQuestionId(question.runtimeQuestionId());
            entity.setSourceSlideId(question.sourceSlideId());
            entity.setBankQuestionId(question.bankQuestionId());
            entity.setKind(question.kind());
            entity.setAuthoringType(question.authoringType());
            entity.setPoints(question.points());
            entity.setAutoGradable(question.autoGradable());
            entity.setGradingPolicy(question.gradingPolicy());
            entity.setOrderIndex(question.orderIndex());
            entity.setBody(question.body());
            entity.setAnswerKey(question.answerKey().isEmpty() ? null : question.answerKey());
            versionQuestionRepository.save(entity);
        }
    }

    private CompiledQuiz compileWithSettings(String tenantId, String quizId, QuizDraft draft) {
        List<String> bankQuestionIds = extractBankQuestionIds(draft.getTree());
        Map<String, Map<String, Object>> questionSettings = new LinkedHashMap<>();
        if (!bankQuestionIds.isEmpty()) {
            bankQuestionRepository.findByTenantIdAndIdIn(tenantId, bankQuestionIds).forEach(question -> {
                if (question.getSettings() != null && !question.getSettings().isEmpty()) {
                    questionSettings.put(question.getId(), question.getSettings());
                }
            });
        }

        Map<String, Map<String, Object>> questionSetSettings = new LinkedHashMap<>();
        quizQuestionRepository.findByTenantIdAndQuizIdOrderByOrderIndexAsc(tenantId, quizId).forEach(link -> {
            if (link.getSettings() != null && !link.getSettings().isEmpty()) {
                questionSetSettings.put(link.getQuestionId(), link.getSettings());
            }
        });
        return compileService.compile(draft.getSettings(), draft.getTree(), questionSettings, questionSetSettings);
    }

    private List<String> extractBankQuestionIds(Object tree) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (Map<String, Object> group : asMapList(tree)) {
            Object slides = group.get("slides");
            for (Map<String, Object> slide : asMapList(slides)) {
                Object bankQuestionId = firstNonNull(slide.get("bankQuestionId"), slide.get("questionId"));
                if (bankQuestionId instanceof String value && !value.isBlank()) {
                    ids.add(value.trim());
                }
            }
        }
        return List.copyOf(ids);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private QuizPackage buildPackage(
            String tenantId,
            Quiz quiz,
            QuizVersion version,
            CompiledQuiz compiled,
            String gradingMode,
            String contentHash
    ) {
        // server-authoritative packages must never embed the answer key (spec §6 anti-leak).
        Map<String, Object> packageJson = new LinkedHashMap<>();
        packageJson.put("schemaVersion", SCHEMA_VERSION);
        packageJson.put("quizId", quiz.getId());
        packageJson.put("version", version.getVersionLabel());
        packageJson.put("gradingMode", gradingMode);
        packageJson.put("quiz", compiled.runtimeQuiz());
        if (MODE_CLIENT_FIRST.equals(gradingMode)) {
            // client-first (training/homework) MAY embed the key so the FE engine can grade offline.
            packageJson.put("answerKey", compiled.answerKey());
        }

        String hashPrefix = contentHash.length() >= 8 ? contentHash.substring(0, 8) : contentHash;
        String packageId = "pkg_" + quiz.getId() + "_" + version.getVersionLabel() + "_" + hashPrefix;
        String signature = packageSigner.sign(canonicalJson(packageJson));

        QuizPackage pkg = new QuizPackage();
        pkg.setId(packageId);
        pkg.setQuizId(quiz.getId());
        pkg.setQuizVersionId(version.getId());
        pkg.setQuizVersion(version.getVersionLabel());
        pkg.setTenantId(tenantId);
        pkg.setContentHash(contentHash);
        pkg.setSignature(signature);
        pkg.setPublicKeyId(packageSigner.publicKeyId());
        pkg.setGradingMode(gradingMode);
        pkg.setSchemaVersion(SCHEMA_VERSION);
        pkg.setPackageFormat("single");
        pkg.setAssetsManifest(assetsManifest(compiled.runtimeQuiz()));
        pkg.setPackageJson(packageJson);
        pkg.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pkg;
    }

    private Map<String, Object> assetsManifest(Map<String, Object> runtimeQuiz) {
        // Media manifest is populated when slide media referencing is wired end-to-end; empty is valid.
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("assets", List.of());
        return manifest;
    }

    private Map<String, Object> snapshot(QuizDraft draft, Map<String, Object> curriculumSnapshot) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("settings", draft.getSettings());
        snapshot.put("playerTemplate", draft.getPlayerTemplate());
        snapshot.put("tree", draft.getTree());
        if (curriculumSnapshot != null && !curriculumSnapshot.isEmpty()) {
            snapshot.put("curriculumSnapshot", curriculumSnapshot);
        }
        return snapshot;
    }

    private QuizDraft latestDraft(String tenantId, String quizId) {
        return quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz draft not found"));
    }

    // docVersion is optional (save-on-exit model): null publishes the latest draft as-is; a supplied
    // value still guards against publishing a stale draft when a second editor is open (409 STALE_DRAFT).
    private void assertDocVersion(QuizDraft draft, Long requestedDocVersion) {
        if (requestedDocVersion == null) {
            return;
        }
        if (!requestedDocVersion.equals(draft.getDocVersion())) {
            throw new StaleDraftException(draft.getDocVersion());
        }
    }

    private String normalizeGradingMode(String gradingMode) {
        String value = blankToNull(gradingMode);
        if (value == null) {
            return MODE_CLIENT_FIRST;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.equals(MODE_CLIENT_FIRST) && !normalized.equals(MODE_SERVER_AUTH)) {
            throw new BadRequestException("gradingMode must be 'client-first' or 'server-authoritative'");
        }
        return normalized;
    }

    private String resolveLabel(String requested, int versionNumber) {
        String value = blankToNull(requested);
        return value != null ? value : "v" + versionNumber;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private ValidationErrorResponse toErrorResponse(ValidationError error) {
        return new ValidationErrorResponse(error.path(), error.code(), error.message());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void evictQuizCachesAfterCommit(String tenantId, String quizId) {
        Runnable evict = () -> {
            Cache cache = cacheManager.getCache("quizRuntimePackage");
            if (cache != null) {
                cache.evict(tenantId + ":" + quizId);
            }
            evictAll("lcmsQuestionBankWorkspace");
            evictAll("lcmsQuizWorkspace");
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict.run();
            }
        });
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    // Stable serialization for hashing/signing: keys are ordered so the same content always hashes equal.
    private String canonicalJson(Map<String, Object> payload) {
        return objectMapper.writeValueAsString(payload);
    }

    /** 422 with the field-level error list (spec §4.2 publish returns VALIDATION_FAILED). */
    public static final class ValidationFailedException extends ApiException {
        private final transient List<ValidationError> errors;

        public ValidationFailedException(List<ValidationError> errors) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation Failed",
                    "quiz failed publish validation");
            this.errors = errors;
        }

        public List<ValidationError> errors() {
            return errors;
        }
    }
}
