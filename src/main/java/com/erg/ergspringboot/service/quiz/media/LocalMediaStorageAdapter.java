package com.erg.ergspringboot.service.quiz.media;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;

/**
 * Content-addressed local media storage adapter (spec §5). Default implementation used until a real
 * R2/S3 client is wired. Keys are {@code quiz-media/{tenant}/{sha-prefix}/{sha}{ext}} so identical
 * bytes always resolve to the same object (dedup + immutable CDN caching).
 *
 * <p>Presigned upload URLs point at a local upload endpoint carrying a short expiry hint; a real
 * adapter would return a signed R2/S3 PUT URL instead. The read URL is stable and content-addressed.
 */
public class LocalMediaStorageAdapter implements MediaStorageAdapter {

    static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    private final String publicBaseUrl;
    private final String uploadBaseUrl;
    private final byte[] signingKey;
    private final Clock clock;
    private final Path storageRoot;

    public LocalMediaStorageAdapter(
            @Value("${erg.quiz.media.public-base-url:/api/v1/lcms/media/blob}") String publicBaseUrl,
            @Value("${erg.quiz.media.upload-base-url:/api/v1/lcms/media/upload}") String uploadBaseUrl,
            @Value("${erg.quiz.media.signing-secret:${SECRET_MEDIA_URL_SIGNING_KEY:${SECRET_AUTH__JWT_SECRET:}}}")
            String signingSecret
    ) {
        this(publicBaseUrl, uploadBaseUrl, signingSecret, Clock.systemUTC(), Path.of(".erg-media"));
    }

    LocalMediaStorageAdapter(String publicBaseUrl, String uploadBaseUrl) {
        this(publicBaseUrl, uploadBaseUrl, "test-only-media-signing-key-at-least-32-bytes", Clock.systemUTC(), Path.of(".erg-media"));
    }

    LocalMediaStorageAdapter(String publicBaseUrl, String uploadBaseUrl, String signingSecret, Clock clock) {
        this(publicBaseUrl, uploadBaseUrl, signingSecret, clock, Path.of(".erg-media"));
    }

    LocalMediaStorageAdapter(
            String publicBaseUrl, String uploadBaseUrl, String signingSecret, Clock clock, Path storageRoot
    ) {
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalArgumentException("media signing secret must contain at least 32 characters");
        }
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.uploadBaseUrl = trimTrailingSlash(uploadBaseUrl);
        this.signingKey = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    @Override
    public String storageKey(String tenantId, String sha256, String mimeType) {
        String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "00";
        return "quiz-media/" + tenantId + "/" + prefix + "/" + sha256 + extension(mimeType);
    }

    @Override
    public String presignUpload(String storageKey, String mimeType, long byteSize) {
        long expiresAtEpochSecond = clock.instant().getEpochSecond() + PRESIGN_TTL.toSeconds();
        String tenantId = tenantFrom(storageKey);
        String signature = sign(tenantId, storageKey, mimeType, byteSize, expiresAtEpochSecond);
        return uploadBaseUrl + "/" + storageKey + "?expires=" + expiresAtEpochSecond + "&signature=" + signature;
    }

    @Override
    public boolean isAuthorizedUpload(String uploadUrl, String tenantId, String mimeType, long byteSize) {
        try {
            URI uri = URI.create(uploadUrl);
            String prefix = URI.create(uploadBaseUrl).getPath() + "/";
            String path = uri.getPath();
            if (path == null || !path.startsWith(prefix)) return false;
            String storageKey = path.substring(prefix.length());
            if (!storageKey.startsWith("quiz-media/" + tenantId + "/")) return false;
            Map<String, String> query = parseQuery(uri.getRawQuery());
            long expires = Long.parseLong(query.getOrDefault("expires", "0"));
            if (expires < clock.instant().getEpochSecond()) return false;
            String expected = sign(tenantId, storageKey, mimeType, byteSize, expires);
            String actual = query.getOrDefault("signature", "");
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    actual.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
    }

    @Override
    public Optional<StoredObject> headObject(String storageKey) {
        Path object = resolve(storageKey);
        if (!Files.isRegularFile(object)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(object);
            return Optional.of(new StoredObject(bytes.length, sha256(bytes), mimeType(storageKey)));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot inspect local media object", exception);
        }
    }

    @Override
    public boolean storeAuthorizedUpload(String uploadUrl, String tenantId, String mimeType, byte[] bytes) {
        if (bytes == null || !isAuthorizedUpload(uploadUrl, tenantId, mimeType, bytes.length)) return false;
        URI uri = URI.create(uploadUrl);
        String prefix = URI.create(uploadBaseUrl).getPath() + "/";
        String storageKey = uri.getPath().substring(prefix.length());
        int extension = storageKey.lastIndexOf('.');
        if (extension < 0) return false;
        String expectedSha = storageKey.substring(storageKey.lastIndexOf('/') + 1, extension);
        if (!MessageDigest.isEqual(expectedSha.getBytes(StandardCharsets.US_ASCII),
                sha256(bytes).getBytes(StandardCharsets.US_ASCII))) return false;
        Path object = resolve(storageKey);
        try {
            Files.createDirectories(object.getParent());
            Path temporary = Files.createTempFile(object.getParent(), ".upload-", ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, object, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, object, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot store local media object", exception);
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) throw new IllegalArgumentException("storage key escapes media root");
        return resolved;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String mimeType(String storageKey) {
        if (storageKey.endsWith(".png")) return "image/png";
        if (storageKey.endsWith(".jpg")) return "image/jpeg";
        if (storageKey.endsWith(".webp")) return "image/webp";
        if (storageKey.endsWith(".gif")) return "image/gif";
        if (storageKey.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private String extension(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> "";
        };
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String tenantFrom(String storageKey) {
        String[] segments = storageKey.split("/", 3);
        if (segments.length < 3 || !"quiz-media".equals(segments[0]) || segments[1].isBlank()) {
            throw new IllegalArgumentException("storage key is not tenant-bound quiz media");
        }
        return segments[1];
    }

    private String sign(String tenantId, String storageKey, String mimeType, long byteSize, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            String contract = tenantId + "\n" + storageKey + "\n" + mimeType + "\n" + byteSize + "\n" + expires;
            return HexFormat.of().formatHex(mac.doFinal(contract.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return Map.of();
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
    }
}
