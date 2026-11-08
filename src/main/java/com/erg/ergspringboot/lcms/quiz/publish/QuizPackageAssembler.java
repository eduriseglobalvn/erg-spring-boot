package com.erg.ergspringboot.lcms.quiz.publish;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the learner package for a published quiz version: the package payload, its canonical
 * serialization for hashing/signing, and the signed {@link QuizPackage} row.
 */
final class QuizPackageAssembler {

    static final int SCHEMA_VERSION = 1;
    static final String MODE_CLIENT_FIRST = "client-first";

    private final QuizPackageSigner packageSigner;
    private final ObjectMapper objectMapper;

    QuizPackageAssembler(QuizPackageSigner packageSigner, ObjectMapper objectMapper) {
        this.packageSigner = packageSigner;
        this.objectMapper = objectMapper;
    }

    QuizPackage buildPackage(
            String tenantId,
            Quiz quiz,
            QuizVersion version,
            String gradingMode,
            String contentHash,
            Map<String, Object> packageJson,
            String canonicalPackagePayload
    ) {
        // server-authoritative packages must never embed the answer key (spec §6 anti-leak).
        String hashPrefix = contentHash.length() >= 8 ? contentHash.substring(0, 8) : contentHash;
        String packageId = "pkg_" + quiz.getId() + "_" + version.getVersionLabel() + "_" + hashPrefix;
        String signature = packageSigner.sign(canonicalPackagePayload);

        QuizPackage pkg = new QuizPackage();
        pkg.setId(packageId);
        pkg.setQuizId(quiz.getId());
        pkg.setQuizVersionId(version.getId());
        pkg.setQuizVersion(version.getVersionLabel());
        pkg.setTenantId(tenantId);
        pkg.setContentHash(contentHash);
        pkg.setSignature(signature);
        pkg.setPublicKeyId(packageSigner.publicKeyId());
        pkg.setSignatureAlgorithm(packageSigner.signatureAlgorithm());
        pkg.setGradingMode(gradingMode);
        pkg.setSchemaVersion(SCHEMA_VERSION);
        pkg.setPackageFormat("single");
        pkg.setAssetsManifest(asMap(packageJson.get("assetsManifest")));
        pkg.setPackageJson(packageJson);
        pkg.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pkg;
    }

    Map<String, Object> packagePayload(
            String quizId,
            String versionLabel,
            CompiledQuiz compiled,
            String gradingMode
    ) {
        PortableQuizAssetBundler.Bundle bundledAssets = PortableQuizAssetBundler.bundle(compiled.runtimeQuiz());
        Map<String, Object> packageJson = new LinkedHashMap<>();
        packageJson.put("schemaVersion", SCHEMA_VERSION);
        packageJson.put("quizId", quizId);
        packageJson.put("version", versionLabel);
        packageJson.put("gradingMode", gradingMode);
        boolean clientFirst = MODE_CLIENT_FIRST.equals(gradingMode);
        packageJson.put("quiz", clientFirst ? bundledAssets.runtimeQuiz() : withoutQuestionFeedback(bundledAssets.runtimeQuiz()));
        packageJson.put("assetsManifest", bundledAssets.assetsManifest());
        if (clientFirst) {
            packageJson.put("answerKey", compiled.answerKey());
        }
        return packageJson;
    }

    /**
     * A graded (server-authoritative) quiz must not hand out the teacher's per-question feedback texts before the
     * learner answers: "Correct, it is X" would be the answer key in words. The keys stay so players read empty text.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> withoutQuestionFeedback(Map<String, Object> quiz) {
        Map<String, Object> copy = new LinkedHashMap<>(quiz);
        if (!(quiz.get("sections") instanceof Collection<?> sections)) {
            return copy;
        }
        copy.put("sections", sections.stream().map(section -> {
            Map<String, Object> sectionCopy = new LinkedHashMap<>((Map<String, Object>) section);
            if (sectionCopy.get("questions") instanceof Collection<?> questions) {
                sectionCopy.put("questions", questions.stream().map(question -> {
                    Map<String, Object> questionCopy = new LinkedHashMap<>((Map<String, Object>) question);
                    if (questionCopy.containsKey("feedback")) {
                        questionCopy.put("feedback", Map.of("correct", "", "partial", "", "incorrect", ""));
                    }
                    return questionCopy;
                }).toList());
            }
            return sectionCopy;
        }).toList());
        return copy;
    }

    // Stable serialization for hashing/signing: keys are ordered so the same content always hashes equal.
    String canonicalJson(Map<String, Object> payload) {
        return objectMapper.writeValueAsString(canonicalValue(payload));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalValue).toList();
        }
        if (value instanceof BigDecimal decimal) {
            BigDecimal normalized = decimal.stripTrailingZeros();
            return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("package payload contains a non-finite number");
            }
            return canonicalValue(BigDecimal.valueOf(number));
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw new IllegalArgumentException("package payload contains a non-finite number");
            }
            return canonicalValue(BigDecimal.valueOf(number.doubleValue()));
        }
        return value;
    }
}
