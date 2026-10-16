package com.erg.ergspringboot.service.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.controller.lcms.LcmsMediaController;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lcms.LcmsOperations;
import com.erg.ergspringboot.lcms.LcmsCommandCompatibility;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * HTTP-path acceptance for the local signed-upload endpoint (BE-017). Proves the controller only
 * stores bytes when the presigned URL is unexpired, untampered, tenant-bound and checksum-matched,
 * exercising the exact request the presign URL points at rather than only the adapter in isolation.
 * Lives in the adapter package to use the deterministic-clock/temp-root constructor without widening
 * production API visibility.
 */
class LcmsMediaControllerUploadTest {

    private static final String SECRET = "media-signing-test-key-with-32-bytes-minimum";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-24T08:00:00Z"), ZoneOffset.UTC);
    private static final String BASE = "/api/v1/lcms/media/upload";

    @TempDir
    Path storageRoot;

    private LocalMediaStorageAdapter storage;
    private LcmsMediaController controller;

    @BeforeEach
    void setUp() {
        storage = new LocalMediaStorageAdapter(
                "/api/v1/lcms/media/blob", BASE, SECRET, CLOCK, storageRoot);
        controller = new LcmsMediaController(operationsFor(storage));
    }

    private static LcmsOperations operationsFor(LocalMediaStorageAdapter storage) {
        MediaService media = new MediaService(
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.repository.quiz.media.MediaAssetRepository.class),
                storage);
        return new LcmsOperations(LcmsCommandCompatibility.fromServices(
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService.class),
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService.class),
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.service.quiz.authoring.QuizDraftService.class),
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.service.quiz.publish.QuizPublishService.class),
                media,
                org.mockito.Mockito.mock(
                        com.erg.ergspringboot.service.quiz.bundle.PortableQuizBundleService.class)));
    }

    @Test
    void storesBytesWhenSignatureTenantAndChecksumAllMatch() {
        byte[] bytes = "committed-slide-image".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.storageKey("tenant-a", sha, "image/png");
        String url = storage.presignUpload(key, "image/png", bytes.length);

        ResponseEntity<Void> response = upload(url, "tenant-a", "image/png", bytes);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(storage.headObject(key)).isPresent();
    }

    @Test
    void rejectsTamperedSignature() {
        byte[] bytes = "tampered".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.storageKey("tenant-a", sha, "image/png");
        String url = storage.presignUpload(key, "image/png", bytes.length).replace("signature=", "signature=00");

        assertThatThrownBy(() -> upload(url, "tenant-a", "image/png", bytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void rejectsExpiredUrl() {
        byte[] bytes = "expired".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.storageKey("tenant-a", sha, "image/png");
        String url = storage.presignUpload(key, "image/png", bytes.length);

        LocalMediaStorageAdapter afterExpiry = new LocalMediaStorageAdapter(
                "/api/v1/lcms/media/blob", BASE, SECRET,
                Clock.fixed(Instant.parse("2026-07-24T08:06:00Z"), ZoneOffset.UTC), storageRoot);
        LcmsMediaController expiredController = new LcmsMediaController(operationsFor(afterExpiry));

        assertThatThrownBy(() -> upload(expiredController, url, "tenant-a", "image/png", bytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void rejectsCrossTenantReuseOfAnotherTenantsSignedUrl() {
        byte[] bytes = "cross-tenant".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.storageKey("tenant-a", sha, "image/png");
        String url = storage.presignUpload(key, "image/png", bytes.length);

        assertThatThrownBy(() -> upload(url, "tenant-b", "image/png", bytes))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void rejectsChecksumMismatchBetweenSignedKeyAndUploadedBytes() {
        byte[] presignedBytes = "declared".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(presignedBytes);
        String key = storage.storageKey("tenant-a", sha, "image/png");
        String url = storage.presignUpload(key, "image/png", presignedBytes.length);

        byte[] swapped = "different-bytes-same-length".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> upload(url, "tenant-a", "image/png", swapped))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void rejectsOversizeUploadBeforeStorage() {
        byte[] bytes = "oversize".getBytes(StandardCharsets.UTF_8);
        String url = storage.presignUpload(
                storage.storageKey("tenant-a", sha256(bytes), "image/png"), "image/png", bytes.length);

        byte[] tooBig = new byte[(int) (MediaService.MAX_IMAGE_BYTES + 1)];
        assertThatThrownBy(() -> upload(url, "tenant-a", "image/png", tooBig))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum allowed size");
    }

    private ResponseEntity<Void> upload(String url, String tenantId, String mimeType, byte[] bytes) {
        return upload(controller, url, tenantId, mimeType, bytes);
    }

    private ResponseEntity<Void> upload(
            LcmsMediaController target, String url, String tenantId, String mimeType, byte[] bytes) {
        URI uri = URI.create(url);
        String[] segments = uri.getPath().split("/");
        String prefix = segments[segments.length - 2];
        String filename = segments[segments.length - 1];
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", uri.getPath());
        request.setQueryString(uri.getRawQuery());
        return target.uploadLocal(tenantId, prefix, filename, mimeType, bytes, request);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
