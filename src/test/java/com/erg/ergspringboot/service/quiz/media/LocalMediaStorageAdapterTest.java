package com.erg.ergspringboot.service.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaStorageAdapterTest {

    @TempDir
    Path storageRoot;

    private static final String SECRET = "media-signing-test-key-with-32-bytes-minimum";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC);

    private final LocalMediaStorageAdapter adapter =
            new LocalMediaStorageAdapter("/blob", "/upload", SECRET, CLOCK);

    @Test
    void authorizesOnlyTheOriginalTenantBoundUploadContract() {
        String key = adapter.storageKey("tenant-a", "a".repeat(64), "image/png");
        String url = adapter.presignUpload(key, "image/png", 2048);

        assertThat(adapter.isAuthorizedUpload(url, "tenant-a", "image/png", 2048)).isTrue();
        assertThat(adapter.isAuthorizedUpload(url, "tenant-b", "image/png", 2048)).isFalse();
        assertThat(adapter.isAuthorizedUpload(url, "tenant-a", "image/png", 2049)).isFalse();
    }

    @Test
    void rejectsTamperedOrExpiredUploadUrls() {
        String key = adapter.storageKey("tenant-a", "b".repeat(64), "image/webp");
        String url = adapter.presignUpload(key, "image/webp", 4096);

        assertThat(adapter.isAuthorizedUpload(url.replace("signature=", "signature=00"),
                "tenant-a", "image/webp", 4096)).isFalse();

        LocalMediaStorageAdapter afterExpiry = new LocalMediaStorageAdapter(
                "/blob", "/upload", SECRET,
                Clock.fixed(Instant.parse("2026-07-22T08:06:00Z"), ZoneOffset.UTC));
        assertThat(afterExpiry.isAuthorizedUpload(url, "tenant-a", "image/webp", 4096)).isFalse();
    }

    @Test
    void storesAuthorizedBytesAndReturnsChecksumVerifiedHeadMetadata() {
        byte[] bytes = "verified-local-media".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sha = java.util.HexFormat.of().formatHex(hash(bytes));
        LocalMediaStorageAdapter local = new LocalMediaStorageAdapter(
                "/blob", "/upload", SECRET, CLOCK, storageRoot);
        String key = local.storageKey("tenant-a", sha, "image/png");
        String url = local.presignUpload(key, "image/png", bytes.length);

        assertThat(local.storeAuthorizedUpload(url, "tenant-a", "image/png", bytes)).isTrue();

        assertThat(local.headObject(key)).contains(
                new MediaStorageAdapter.StoredObject(bytes.length, sha, "image/png"));
    }

    private byte[] hash(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
