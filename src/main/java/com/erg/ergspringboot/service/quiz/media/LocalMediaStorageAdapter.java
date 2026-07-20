package com.erg.ergspringboot.service.quiz.media;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Content-addressed local media storage adapter (spec §5). Default implementation used until a real
 * R2/S3 client is wired. Keys are {@code quiz-media/{tenant}/{sha-prefix}/{sha}{ext}} so identical
 * bytes always resolve to the same object (dedup + immutable CDN caching).
 *
 * <p>Presigned upload URLs point at a local upload endpoint carrying a short expiry hint; a real
 * adapter would return a signed R2/S3 PUT URL instead. The read URL is stable and content-addressed.
 */
@Component
public class LocalMediaStorageAdapter implements MediaStorageAdapter {

    static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    private final String publicBaseUrl;
    private final String uploadBaseUrl;

    public LocalMediaStorageAdapter(
            @Value("${erg.quiz.media.public-base-url:/api/v1/lcms/media/blob}") String publicBaseUrl,
            @Value("${erg.quiz.media.upload-base-url:/api/v1/lcms/media/upload}") String uploadBaseUrl
    ) {
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.uploadBaseUrl = trimTrailingSlash(uploadBaseUrl);
    }

    @Override
    public String storageKey(String tenantId, String sha256, String mimeType) {
        String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "00";
        return "quiz-media/" + tenantId + "/" + prefix + "/" + sha256 + extension(mimeType);
    }

    @Override
    public String presignUpload(String storageKey, String mimeType, long byteSize) {
        long expiresAtEpochSecond = System.currentTimeMillis() / 1000L + PRESIGN_TTL.toSeconds();
        return uploadBaseUrl + "/" + storageKey + "?expires=" + expiresAtEpochSecond;
    }

    @Override
    public String publicUrl(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
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
}
