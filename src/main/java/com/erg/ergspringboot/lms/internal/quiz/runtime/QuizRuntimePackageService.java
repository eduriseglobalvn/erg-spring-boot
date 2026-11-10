package com.erg.ergspringboot.lms.internal.quiz.runtime;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ContentScope;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizResponse;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the compiled runtime package for a quiz from the published {@code quiz_packages} lineage
 * (spec §1.3, §4.4). Prefers the active version's package; returns empty so the caller can fall back to
 * the legacy monolithic quiz for quizzes that predate the version lineage.
 *
 * <p>Security invariant (spec §6): a {@code server-authoritative} package never carries the answer key.
 * The stored {@code package_json} already excludes it for that mode (enforced at publish time); this
 * service additionally strips any {@code answerKey} defensively before returning.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class QuizRuntimePackageService {

    private static final String MODE_SERVER_AUTH = "server-authoritative";

    private final QuizPackageRepository packageRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final QuizRepository quizRepository;

    public QuizRuntimePackageService(
            QuizPackageRepository packageRepository,
            QuizVersionRepository quizVersionRepository,
            QuizRepository quizRepository
    ) {
        this.packageRepository = packageRepository;
        this.quizVersionRepository = quizVersionRepository;
        this.quizRepository = quizRepository;
    }

    /** Resolves the active published package for a quiz, or empty when none is published yet. */
    @Cacheable(
            cacheNames = "quizRuntimePackage",
            key = "#tenantId + ':' + #quizId",
            sync = true
    )
    public Optional<QuizPackageResponse> activePackage(String tenantId, String quizId) {
        return quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .flatMap(version -> packageRepository.findByQuizVersionIdAndTenantId(version.getId(), tenantId)
                        .map(pkg -> toResponse(quizId, version, pkg)));
    }

    public Optional<QuizPackage> activePackageEntity(String tenantId, String quizId) {
        return quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .flatMap(version -> packageRepository.findByQuizVersionIdAndTenantId(version.getId(), tenantId));
    }

    /** The package of a specific (pinned) quiz version; used when an assignment pinned a version at assign time. */
    public Optional<QuizPackage> packageEntityForVersion(String tenantId, String quizId, String quizVersionId) {
        return quizVersionRepository.findByIdAndTenantId(quizVersionId, tenantId)
                .filter(version -> quizId.equals(version.getQuizId()))
                .flatMap(version -> packageRepository.findByQuizVersionIdAndTenantId(version.getId(), tenantId));
    }

    /** A package by its own id (offline result import); empty when it is not in the tenant. */
    public Optional<QuizPackage> packageEntityById(String tenantId, String packageId) {
        return packageRepository.findByIdAndTenantId(packageId, tenantId);
    }

    public Optional<QuizPackageResponse> packageForVersion(String tenantId, String quizId, String quizVersionId) {
        return quizVersionRepository.findByIdAndTenantId(quizVersionId, tenantId)
                .filter(version -> quizId.equals(version.getQuizId()))
                .flatMap(version -> packageRepository.findByQuizVersionIdAndTenantId(version.getId(), tenantId)
                        .map(pkg -> toResponse(quizId, version, pkg)));
    }

    /** Learner view of a package: the answer key is removed unless the caller explicitly allows offline grading. */
    public static QuizPackageResponse withoutAnswerKey(QuizPackageResponse response) {
        if (response == null || response.answerKey() == null || response.answerKey().isEmpty()) return response;
        return new QuizPackageResponse(response.version(), response.packageHash(), response.contentHash(),
                response.signature(), response.gradingMode(), response.quiz(), Map.of(), response.id());
    }

    /**
     * The editor's runtime settings carry no player mode, so the player treated every quiz as practice: a test got
     * per-question feedback and no countdown. The mode comes from the quiz kind, the one the attempt uses
     * ({@code AttemptFactory}); an explicit mode in the settings still wins.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> playerSettings(Object runtimeSettings, String quizKind) {
        Map<String, Object> result = runtimeSettings instanceof Map<?, ?> map
                ? new java.util.LinkedHashMap<>((Map<String, Object>) map)
                : new java.util.LinkedHashMap<>();
        if (quizKind != null && !quizKind.isBlank()) {
            String kind = quizKind.trim().toLowerCase(java.util.Locale.ROOT);
            result.putIfAbsent("mode", kind.equals("test") || kind.equals("testing") ? "testing" : "training");
        }
        return result;
    }

    private String quizKind(String quizId, QuizVersion version) {
        return quizRepository.findByIdAndTenantId(quizId, version.getTenantId()).map(quiz -> quiz.getKind()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private QuizPackageResponse toResponse(String quizId, QuizVersion version, QuizPackage pkg) {
        Map<String, Object> packageJson = pkg.getPackageJson() == null ? Map.of() : pkg.getPackageJson();
        Map<String, Object> runtimeQuiz = packageJson.get("quiz") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : (version.getRuntimeQuiz() == null ? Map.of() : version.getRuntimeQuiz());

        boolean serverAuthoritative = MODE_SERVER_AUTH.equals(pkg.getGradingMode());
        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        if (runtimeQuiz.get("info") != null) {
            settings.put("info", runtimeQuiz.get("info"));
        }
        if (runtimeQuiz.get("theme") != null) {
            settings.put("theme", runtimeQuiz.get("theme"));
        }
        settings.put("settings", playerSettings(runtimeQuiz.get("settings"), quizKind(quizId, version)));
        if (runtimeQuiz.get("playerTemplate") != null) {
            settings.put("playerTemplate", runtimeQuiz.get("playerTemplate"));
        }

        QuizResponse quizResponse = new QuizResponse(
                quizId,
                new ContentScope(null, null),
                stringValue(runtimeQuiz.get("info"), "title"),
                null,
                null,
                null,
                List.of(),
                List.of(),
                "published",
                version.getVersionNumber() == null ? 1 : version.getVersionNumber(),
                pkg.getContentHash(),
                pkg.getCreatedAt(),
                pkg.getUpdatedAt()
        );

        // Defensive: server-authoritative packages must never expose the key to the client.
        Map<String, Object> runtimeForClient = new java.util.LinkedHashMap<>(runtimeQuiz);
        Map<String, Object> answerKey = Map.of();
        if (serverAuthoritative) {
            runtimeForClient.remove("answerKey");
        } else if (packageJson.get("answerKey") instanceof Map<?, ?> map) {
            answerKey = new java.util.LinkedHashMap<>((Map<String, Object>) map);
        }

        QuizDetailResponse detail = new QuizDetailResponse(
                quizResponse,
                sections(runtimeForClient),
                settings,
                runtimeQuiz.get("result") instanceof Map<?, ?> result ? (Map<String, Object>) result : Map.of(),
                runtimeQuiz.get("theme") instanceof Map<?, ?> theme ? (Map<String, Object>) theme : Map.of()
        );
        return new QuizPackageResponse(
                quizResponse.version(),
                pkg.getContentHash(),
                pkg.getContentHash(),
                pkg.getSignature(),
                pkg.getGradingMode(),
                detail,
                answerKey,
                pkg.getId()
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> sections(Map<String, Object> runtimeQuiz) {
        Object sections = runtimeQuiz.get("sections");
        if (sections instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private String stringValue(Object holder, String field) {
        if (holder instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(field);
            return value == null ? null : value.toString();
        }
        return null;
    }
}
