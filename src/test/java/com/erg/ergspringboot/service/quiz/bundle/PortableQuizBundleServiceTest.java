package com.erg.ergspringboot.service.quiz.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortableQuizBundleServiceTest {

    @Mock private QuizVersionRepository quizVersionRepository;
    @Mock private QuizPackageRepository packageRepository;

    private PortableQuizBundleService service() {
        return new PortableQuizBundleService(quizVersionRepository, packageRepository);
    }

    private QuizVersion version() {
        QuizVersion version = new QuizVersion();
        version.setId("ver-1");
        version.setQuizId("quiz-1");
        version.setTenantId("tenant-a");
        version.setVersionLabel("v1");
        version.setVersionNumber(1);
        version.setSchemaVersion(1);
        version.setGradingMode("client-first");
        version.setPublishedAt(OffsetDateTime.parse("2026-08-09T00:00:00Z"));
        return version;
    }

    private QuizPackage pkg(String gradingMode) {
        QuizPackage pkg = new QuizPackage();
        pkg.setId("pkg-1");
        pkg.setQuizId("quiz-1");
        pkg.setQuizVersionId("ver-1");
        pkg.setTenantId("tenant-a");
        pkg.setContentHash("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        pkg.setSignature("sig");
        pkg.setPublicKeyId("key-1");
        pkg.setSignatureAlgorithm("Ed25519");
        pkg.setPackageFormat("single");
        pkg.setDownloadUrl("https://cdn.erg.edu.vn/packages/pkg-1.ergquiz");
        pkg.setGeneratedAt(OffsetDateTime.parse("2026-08-09T00:00:00Z"));
        pkg.setGradingMode(gradingMode);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("quiz", Map.of("sections", java.util.List.of()));
        json.put("answerKey", Map.of("q1", Map.of("correctChoiceId", "a")));
        pkg.setPackageJson(json);
        return pkg;
    }

    @Test
    void clientFirstBundleKeepsAnswerKeyForOfflineGrading() {
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version()));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg("client-first")));

        Map<String, Object> bundle = service().exportActive("tenant-a", "quiz-1");

        assertThat(bundle.get("offlineGradable")).isEqualTo(true);
        assertThat(bundle.get("gradingMode")).isEqualTo("client-first");
        @SuppressWarnings("unchecked")
        Map<String, Object> pkgJson = (Map<String, Object>) bundle.get("package");
        assertThat(pkgJson).containsKey("answerKey");
        @SuppressWarnings("unchecked")
        Map<String, Object> sig = (Map<String, Object>) bundle.get("signatureBlock");
        assertThat(sig).containsEntry("contentHash", "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .containsEntry("publicKeyId", "key-1");
    }

    @Test
    void serverAuthoritativeBundleStripsAnswerKey() {
        QuizVersion version = version();
        version.setGradingMode("server-authoritative");
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg("server-authoritative")));

        Map<String, Object> bundle = service().exportActive("tenant-a", "quiz-1");

        assertThat(bundle.get("offlineGradable")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> pkgJson = (Map<String, Object>) bundle.get("package");
        assertThat(pkgJson).doesNotContainKey("answerKey");
    }

    @Test
    void missingActiveVersionIsNotFound() {
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().exportActive("tenant-a", "quiz-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void serverAuthoritativeBundleRemovesNestedAnswerKeysAndCredentialMaterial() {
        QuizVersion version = version();
        version.setGradingMode("server-authoritative");
        QuizPackage pkg = pkg("server-authoritative");
        pkg.getPackageJson().put("credentials", Map.of("accessToken", "must-not-escape"));
        pkg.getPackageJson().put("sections", java.util.List.of(Map.of("answer_key", Map.of("q1", "a"))));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg));

        @SuppressWarnings("unchecked")
        Map<String, Object> runtime = (Map<String, Object>) service().exportActive("tenant-a", "quiz-1").get("package");
        assertThat(runtime).doesNotContainKeys("answerKey", "credentials");
        assertThat(runtime.toString()).doesNotContain("accessToken", "answer_key");
    }

    @Test
    void packageMetadataIsExposedForVerificationAndUpdateCaching() {
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version()));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg("client-first")));

        Map<String, Object> bundle = service().exportActive("tenant-a", "quiz-1");

        assertThat(bundle).containsEntry("packageFormat", "single")
                .containsEntry("signatureAlgorithm", "Ed25519")
                .containsEntry("portableReady", true)
                .containsEntry("etag", "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Test
    void signedSelfContainedPackageIsPortableReadyBeforeBinaryArtifactIsPublished() {
        QuizPackage pkg = pkg("client-first");
        pkg.setDownloadUrl(null);
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version()));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg));

        assertThat(service().exportActive("tenant-a", "quiz-1"))
                .containsEntry("portableReady", true)
                .containsEntry("downloadUrl", null);
    }

    @Test
    void packageDependingOnRemoteQuestionMediaIsNotPortableReady() {
        QuizPackage pkg = pkg("client-first");
        pkg.setPackageJson(Map.of("quiz", Map.of(
                "sections", List.of(Map.of("questions", List.of(Map.of(
                        "contentImage", Map.of("url", "https://cdn.example.test/question.png"))))))));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(version()));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg));

        assertThat(service().exportActive("tenant-a", "quiz-1"))
                .containsEntry("portableReady", false);
    }

    @Test
    void metadataManifestOmitsPackagePayload() {
        when(packageRepository.findByIdAndTenantId("pkg-1", "tenant-a"))
                .thenReturn(Optional.of(pkg("client-first")));
        when(quizVersionRepository.findByIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(version()));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-1", "tenant-a"))
                .thenReturn(Optional.of(pkg("client-first")));

        var manifest = service().manifest("tenant-a", "pkg-1");

        assertThat(manifest.artifactType()).isEqualTo("exam-package");
        assertThat(manifest.packageId()).isEqualTo("pkg-1");
        assertThat(manifest.downloadUrl()).startsWith("https://");
        assertThat(manifest).hasNoNullFieldsOrPropertiesExcept("sizeBytes", "expiresAt", "minRuntimeVersion");
    }

    @Test
    void updateFeedExcludesCurrentVersionAndRequiresCompatibleRuntime() {
        QuizVersion current = version();
        QuizVersion next = version();
        next.setId("ver-2");
        next.setVersionLabel("v2");
        next.setVersionNumber(2);
        next.setPublishedAt(OffsetDateTime.parse("2026-08-09T01:00:00Z"));
        QuizPackage nextPackage = pkg("client-first");
        nextPackage.setId("pkg-2");
        nextPackage.setQuizVersionId("ver-2");
        nextPackage.setDownloadUrl("https://cdn.erg.edu.vn/packages/pkg-2.ergquiz");
        nextPackage.setMinRuntimeVersion("1.2.0");
        nextPackage.setReleaseChannel("stable");

        when(quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc(
                eq("quiz-1"), eq("tenant-a"), any()))
                .thenReturn(List.of(next, current));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-2", "tenant-a"))
                .thenReturn(Optional.of(nextPackage));

        var feed = service().updates("tenant-a", "quiz-1", "stable", 1, "1.2.5");

        assertThat(feed.available()).extracting("packageId").containsExactly("pkg-2");
        assertThat(feed.latest().versionNumber()).isEqualTo(2);
        assertThat(feed.etag()).startsWith("sha256:");
    }

    @Test
    void updateFeedRejectsUnknownChannelAndIncompatibleRuntime() {
        assertThatThrownBy(() -> service().updates("tenant-a", "quiz-1", "preview", null, null))
                .isInstanceOf(com.erg.ergspringboot.exception.BadRequestException.class);

        QuizVersion next = version();
        next.setId("ver-2");
        next.setVersionNumber(2);
        QuizPackage nextPackage = pkg("client-first");
        nextPackage.setId("pkg-2");
        nextPackage.setQuizVersionId("ver-2");
        nextPackage.setMinRuntimeVersion("2.0.0");
        when(quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc(eq("quiz-1"), eq("tenant-a"), any()))
                .thenReturn(List.of(next));
        when(packageRepository.findByQuizVersionIdAndTenantId("ver-2", "tenant-a"))
                .thenReturn(Optional.of(nextPackage));

        assertThat(service().updates("tenant-a", "quiz-1", "stable", null, "1.9.9").available()).isEmpty();
    }
}
