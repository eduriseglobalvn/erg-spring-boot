package com.erg.ergspringboot.lcms;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned portable-exam manifest and bounded package payload. */
public record LcmsQuizBundleResponse(
        int bundleFormatVersion,
        String tenantId,
        String quizId,
        String quizVersionId,
        String versionLabel,
        int versionNumber,
        int schemaVersion,
        String gradingMode,
        boolean offlineGradable,
        String packageId,
        String packageFormat,
        @JsonProperty("package") Map<String, Object> packagePayload,
        Map<String, Object> assetsManifest,
        SignatureBlock signatureBlock,
        String signatureAlgorithm,
        String minRuntimeVersion,
        String downloadUrl,
        Long sizeBytes,
        String releaseChannel,
        OffsetDateTime expiresAt,
        boolean portableReady,
        String etag,
        String generatedAt
) {

    /** Compatibility constructor for callers that only know the v1 envelope. */
    public LcmsQuizBundleResponse(
            int bundleFormatVersion,
            String tenantId,
            String quizId,
            String quizVersionId,
            String versionLabel,
            int versionNumber,
            int schemaVersion,
            String gradingMode,
            boolean offlineGradable,
            String packageId,
            Map<String, Object> packagePayload,
            Map<String, Object> assetsManifest,
            SignatureBlock signatureBlock,
            String generatedAt
    ) {
        this(bundleFormatVersion, tenantId, quizId, quizVersionId, versionLabel, versionNumber,
                schemaVersion, gradingMode, offlineGradable, packageId, "single", packagePayload,
                assetsManifest, signatureBlock, signatureBlock == null ? null : signatureBlock.algorithm(),
                null, null, null, "stable", null, false,
                signatureBlock == null ? null : signatureBlock.contentHash(), generatedAt);
    }

    public record RevokeResponse(String packageId, String etag, OffsetDateTime revokedAt) {
    }

    public LcmsQuizBundleResponse {
        packagePayload = copy(packagePayload);
        assetsManifest = copy(assetsManifest);
        signatureBlock = signatureBlock == null ? new SignatureBlock(null, null, null, null) : signatureBlock;
    }

    /** Converts the legacy builder output without allowing arbitrary top-level fields to leak. */
    public static LcmsQuizBundleResponse fromLegacy(Map<String, Object> legacy) {
        if (legacy == null) throw new IllegalArgumentException("quiz bundle must not be null");
        Map<String, Object> signature = map(legacy.get("signatureBlock"));
        return new LcmsQuizBundleResponse(
                number(legacy, "bundleFormatVersion"),
                text(legacy, "tenantId"),
                text(legacy, "quizId"),
                text(legacy, "quizVersionId"),
                text(legacy, "versionLabel"),
                number(legacy, "versionNumber"),
                number(legacy, "schemaVersion"),
                text(legacy, "gradingMode"),
                Boolean.TRUE.equals(legacy.get("offlineGradable")),
                text(legacy, "packageId"),
                defaultText(legacy, "packageFormat", "single"),
                map(legacy.get("package")),
                map(legacy.get("assetsManifest")),
                new SignatureBlock(
                        text(signature, "contentHash"),
                        text(signature, "signature"),
                        text(signature, "publicKeyId"),
                        text(signature, "algorithm") != null
                                ? text(signature, "algorithm")
                                : text(legacy, "signatureAlgorithm")),
                text(legacy, "signatureAlgorithm"),
                text(legacy, "minRuntimeVersion"),
                text(legacy, "downloadUrl"),
                longValue(legacy, "sizeBytes"),
                defaultText(legacy, "releaseChannel", "stable"),
                dateTime(legacy, "expiresAt"),
                Boolean.TRUE.equals(legacy.get("portableReady")),
                text(legacy, "etag"),
                text(legacy, "generatedAt"));
    }

    public record SignatureBlock(String contentHash, String signature, String publicKeyId, String algorithm) {
        public SignatureBlock(String contentHash, String signature, String publicKeyId) {
            this(contentHash, signature, publicKeyId, null);
        }
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException(key + " must be numeric");
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultText(Map<String, Object> values, String key, String fallback) {
        String value = text(values, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Long longValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static OffsetDateTime dateTime(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof OffsetDateTime dateTime) return dateTime;
        if (value instanceof String text && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? copy((Map<String, Object>) map) : Map.of();
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }
}
