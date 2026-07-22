package com.erg.ergspringboot.service.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LocalMediaStorageAdapterTest {

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
}
