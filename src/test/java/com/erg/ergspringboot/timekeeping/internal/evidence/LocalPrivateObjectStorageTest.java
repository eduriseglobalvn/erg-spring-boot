package com.erg.ergspringboot.timekeeping.internal.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPrivateObjectStorageTest {

    private static final String SECRET = "test-only-timekeeping-photo-secret-0123456789";
    private static final String KEY = "private/timekeeping/tenant-a/2026/11/abc.jpg";
    private static final Instant NOW = Instant.parse("2026-11-06T01:00:00Z");

    @TempDir Path root;

    private LocalPrivateObjectStorage storage(Instant now) {
        return new LocalPrivateObjectStorage(root, SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void putThenDeleteRoundTripsUnderTheRoot() throws Exception {
        LocalPrivateObjectStorage storage = storage(NOW);
        storage.put(KEY, new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(Files.readAllBytes(root.resolve(KEY))).containsExactly(1, 2, 3);
        try (var leftovers = Files.list(root.resolve(KEY).getParent())) {
            assertThat(leftovers.map(Path::getFileName).map(Path::toString)).containsExactly("abc.jpg");
        }
        storage.delete(KEY);
        assertThat(root.resolve(KEY)).doesNotExist();
        storage.delete(KEY);
    }

    @Test
    void readReturnsTheBytesWithTheTypeOfTheStoredExtension() {
        LocalPrivateObjectStorage storage = storage(NOW);
        storage.put(KEY, new byte[] {1, 2, 3}, "image/jpeg");
        storage.put("private/timekeeping/tenant-a/2026/11/abc.webp", new byte[] {4}, "image/webp");

        assertThat(storage.read(KEY)).hasValueSatisfying(photo -> {
            assertThat(photo.bytes()).containsExactly(1, 2, 3);
            assertThat(photo.contentType()).isEqualTo("image/jpeg");
        });
        assertThat(storage.read("private/timekeeping/tenant-a/2026/11/abc.webp").orElseThrow().contentType())
                .isEqualTo("image/webp");
        assertThat(storage.read("private/timekeeping/tenant-a/2026/11/missing.jpg")).isEmpty();
        assertThatThrownBy(() -> storage.read("private/timekeeping/../../secret.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readRefusesASymlinkThatLeavesTheRoot(@TempDir Path outside) throws Exception {
        Path secret = Files.writeString(outside.resolve("secret.jpg"), "secret");
        Path link = root.resolve("private/timekeeping/tenant-a/2026/11/link.jpg");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, secret);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException noSymlinks) {
            org.junit.jupiter.api.Assumptions.abort("symlinks are not available here: " + noSymlinks);
        }

        assertThatThrownBy(() -> storage(NOW).read("private/timekeeping/tenant-a/2026/11/link.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traversalAbsoluteAndBackslashKeysAreRejected() {
        LocalPrivateObjectStorage storage = storage(NOW);
        for (String bad : new String[] {
                "private/timekeeping/../../etc/passwd", "../outside.jpg", "/etc/passwd",
                "private\\timekeeping\\a.jpg", "C:/windows/a.jpg", "other/prefix/a.jpg", "", null}) {
            assertThatThrownBy(() -> storage.put(bad, new byte[] {1}, "image/jpeg"))
                    .as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(bad))
                    .as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void presignedUrlVerifiesAndUsesTheSingleFilePath() {
        LocalPrivateObjectStorage storage = storage(NOW);
        URI url = storage.presignGet(KEY, Duration.ofMinutes(5));
        Map<String, String> query = query(url);

        assertThat(url.getPath()).isEqualTo(LocalPrivateObjectStorage.PHOTO_FILE_PATH);
        assertThat(query.get("key")).isEqualTo(KEY);
        assertThat(Long.parseLong(query.get("exp"))).isEqualTo(NOW.plusSeconds(300).getEpochSecond());
        assertThat(storage.verify(KEY, Long.parseLong(query.get("exp")), query.get("sig"))).isTrue();
    }

    @Test
    void tamperedKeyExpiryWrongSignatureAndExpiredAreRejected() {
        URI url = storage(NOW).presignGet(KEY, Duration.ofMinutes(5));
        Map<String, String> query = query(url);
        long exp = Long.parseLong(query.get("exp"));
        String sig = query.get("sig");
        LocalPrivateObjectStorage storage = storage(NOW);

        assertThat(storage.verify("private/timekeeping/tenant-b/2026/11/abc.jpg", exp, sig)).isFalse();
        assertThat(storage.verify(KEY, exp + 3600, sig)).isFalse();
        assertThat(storage.verify(KEY, exp, "00" + sig.substring(2))).isFalse();
        assertThat(storage.verify(KEY, exp, null)).isFalse();
        assertThat(storage(NOW.plusSeconds(301)).verify(KEY, exp, sig)).isFalse();
        assertThat(new LocalPrivateObjectStorage(root, "another-secret-another-secret-0123456789",
                Clock.fixed(NOW, ZoneOffset.UTC)).verify(KEY, exp, sig)).isFalse();
    }

    @Test
    void blankSecretFallsBackToRandomPerProcessSecretAndShortSecretFails() {
        LocalPrivateObjectStorage a = new LocalPrivateObjectStorage(root, "", Clock.fixed(NOW, ZoneOffset.UTC));
        LocalPrivateObjectStorage b = new LocalPrivateObjectStorage(root, null, Clock.fixed(NOW, ZoneOffset.UTC));
        Map<String, String> query = query(a.presignGet(KEY, Duration.ofMinutes(5)));

        assertThat(a.verify(KEY, Long.parseLong(query.get("exp")), query.get("sig"))).isTrue();
        assertThat(b.verify(KEY, Long.parseLong(query.get("exp")), query.get("sig"))).isFalse();
        assertThatThrownBy(() -> new LocalPrivateObjectStorage(root, "short", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, String> query(URI url) {
        Map<String, String> values = new HashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            values.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
}
