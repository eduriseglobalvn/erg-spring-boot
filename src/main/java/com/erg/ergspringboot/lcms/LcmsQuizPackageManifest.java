package com.erg.ergspringboot.lcms;

import java.util.Locale;

/**
 * Metadata-only manifest for an immutable portable exam package.
 *
 * <p>The manifest intentionally does not contain the package JSON or asset
 * bytes. A desktop client can verify the digest/signature and decide whether
 * it is compatible before following the content-addressed download URL.</p>
 */
public record LcmsQuizPackageManifest(
        String manifestVersion,
        String artifactType,
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
        String contentHash,
        String signature,
        String publicKeyId,
        String signatureAlgorithm,
        String minRuntimeVersion,
        String downloadUrl,
        Long sizeBytes,
        String releaseChannel,
        String expiresAt,
        boolean portableReady,
        String etag,
        String generatedAt
) {

    public static LcmsQuizPackageManifest fromBundle(LcmsQuizBundleResponse bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        LcmsQuizBundleResponse.SignatureBlock signature = bundle.signatureBlock();
        return new LcmsQuizPackageManifest(
                "1",
                "exam-package",
                bundle.tenantId(),
                bundle.quizId(),
                bundle.quizVersionId(),
                bundle.versionLabel(),
                bundle.versionNumber(),
                bundle.schemaVersion(),
                bundle.gradingMode(),
                bundle.offlineGradable(),
                bundle.packageId(),
                bundle.packageFormat(),
                signature == null ? null : signature.contentHash(),
                signature == null ? null : signature.signature(),
                signature == null ? null : signature.publicKeyId(),
                bundle.signatureAlgorithm() == null && signature != null
                        ? signature.algorithm()
                        : bundle.signatureAlgorithm(),
                bundle.minRuntimeVersion(),
                bundle.downloadUrl(),
                bundle.sizeBytes(),
                normalizeChannel(bundle.releaseChannel()),
                bundle.expiresAt() == null ? null : bundle.expiresAt().toString(),
                bundle.portableReady(),
                bundle.etag(),
                bundle.generatedAt());
    }

    private static String normalizeChannel(String value) {
        return value == null || value.isBlank() ? "stable" : value.trim().toLowerCase(Locale.ROOT);
    }
}
