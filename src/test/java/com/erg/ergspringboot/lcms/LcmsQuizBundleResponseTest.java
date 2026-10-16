package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LcmsQuizBundleResponseTest {

    @Test
    void legacyBundleIsMappedToTypedEnvelopeAndUnknownTopLevelFieldsAreDropped() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("bundleFormatVersion", 1);
        legacy.put("tenantId", "tenant-a");
        legacy.put("quizId", "quiz-1");
        legacy.put("quizVersionId", "version-1");
        legacy.put("versionLabel", "v1");
        legacy.put("versionNumber", 1);
        legacy.put("schemaVersion", 2);
        legacy.put("gradingMode", "client-first");
        legacy.put("offlineGradable", true);
        legacy.put("packageId", "package-1");
        legacy.put("package", Map.of("sections", java.util.List.of()));
        legacy.put("assetsManifest", Map.of("logo", "asset-1"));
        legacy.put("signatureBlock", Map.of(
                "contentHash", "sha256:abc", "signature", "sig", "publicKeyId", "key-1"));
        legacy.put("generatedAt", "2026-08-08T00:00:00Z");
        legacy.put("internalEntity", Map.of("id", "must-not-escape"));

        LcmsQuizBundleResponse response = LcmsQuizBundleResponse.fromLegacy(legacy);

        assertThat(response.bundleFormatVersion()).isEqualTo(1);
        assertThat(response.packagePayload()).containsKey("sections");
        assertThat(response.signatureBlock().publicKeyId()).isEqualTo("key-1");
        assertThat(response.toString()).doesNotContain("internalEntity");
    }
}
