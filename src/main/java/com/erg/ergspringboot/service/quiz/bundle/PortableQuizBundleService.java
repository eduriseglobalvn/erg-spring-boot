package com.erg.ergspringboot.service.quiz.bundle;

import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a self-contained, offline-playable quiz bundle (spec §7 portable bundle).
 *
 * <p>A bundle is one JSON document carrying everything a disconnected desktop/mobile player needs: the
 * signed runtime package, its content hash + signature + {@code publicKeyId} for offline verification, and
 * the schema version. It is derived entirely from the immutable published lineage ({@code quiz_versions} +
 * {@code quiz_packages}), so a bundle for a given version is byte-stable and reproducible.
 *
 * <p>Security invariant (spec §6): the answer key is included ONLY for {@code client-first} packages (the
 * offline engine needs it to grade locally). {@code server-authoritative} bundles omit it — those quizzes
 * cannot be graded offline by design and must sync answers back for server grading.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class PortableQuizBundleService {

    private static final String MODE_SERVER_AUTH = "server-authoritative";
    private static final int BUNDLE_FORMAT_VERSION = 1;

    private final QuizVersionRepository quizVersionRepository;
    private final QuizPackageRepository packageRepository;

    public PortableQuizBundleService(
            QuizVersionRepository quizVersionRepository,
            QuizPackageRepository packageRepository
    ) {
        this.quizVersionRepository = quizVersionRepository;
        this.packageRepository = packageRepository;
    }

    /** Exports the active published version's bundle. */
    public Map<String, Object> exportActive(String tenantId, String quizId) {
        QuizVersion version = quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz has no active published version"));
        return export(tenantId, version);
    }

    /** Exports a specific version's bundle (any published version, active or superseded). */
    public Map<String, Object> exportVersion(String tenantId, String quizVersionId) {
        QuizVersion version = quizVersionRepository.findByIdAndTenantId(quizVersionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz version not found"));
        return export(tenantId, version);
    }

    private Map<String, Object> export(String tenantId, QuizVersion version) {
        QuizPackage pkg = packageRepository.findByQuizVersionId(version.getId())
                .orElseThrow(() -> new ResourceNotFoundException("published version has no compiled package"));

        boolean serverAuthoritative = MODE_SERVER_AUTH.equals(pkg.getGradingMode());

        Map<String, Object> runtime = pkg.getPackageJson() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(pkg.getPackageJson());
        // Defensive: a server-authoritative bundle must never carry the answer key offline (spec §6).
        if (serverAuthoritative) {
            runtime.remove("answerKey");
        }

        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("contentHash", pkg.getContentHash());
        signature.put("signature", pkg.getSignature());
        signature.put("publicKeyId", pkg.getPublicKeyId());

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("bundleFormatVersion", BUNDLE_FORMAT_VERSION);
        bundle.put("tenantId", tenantId);
        bundle.put("quizId", version.getQuizId());
        bundle.put("quizVersionId", version.getId());
        bundle.put("versionLabel", version.getVersionLabel());
        bundle.put("versionNumber", version.getVersionNumber());
        bundle.put("schemaVersion", version.getSchemaVersion());
        bundle.put("gradingMode", pkg.getGradingMode());
        bundle.put("offlineGradable", !serverAuthoritative);
        bundle.put("packageId", pkg.getId());
        bundle.put("package", runtime);
        bundle.put("assetsManifest", pkg.getAssetsManifest());
        bundle.put("signatureBlock", signature);
        bundle.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return bundle;
    }
}
