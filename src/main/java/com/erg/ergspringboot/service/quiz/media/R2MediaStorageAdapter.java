package com.erg.ergspringboot.service.quiz.media;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3-compatible Cloudflare R2 adapter with checksum-bound PUT and commit-time HEAD verification. */
public final class R2MediaStorageAdapter implements MediaStorageAdapter {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    R2MediaStorageAdapter(S3Client client, S3Presigner presigner, String bucket, String publicBaseUrl) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = requireText(bucket, "R2 bucket name");
        this.publicBaseUrl = trimTrailingSlash(requireText(publicBaseUrl, "R2 public domain"));
    }

    @Override
    public String storageKey(String tenantId, String sha256, String mimeType) {
        String normalizedSha = sha256.toLowerCase(Locale.ROOT);
        return "quiz-media/" + tenantId + "/" + normalizedSha.substring(0, 2) + "/"
                + normalizedSha + extension(mimeType);
    }

    @Override
    public String presignUpload(String storageKey, String mimeType, long byteSize) {
        String checksum = checksumBase64(storageKey);
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentLength(byteSize)
                .contentType(mimeType)
                .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                .checksumSHA256(checksum)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_TTL)
                        .putObjectRequest(put)
                        .build())
                .url()
                .toExternalForm();
    }

    @Override
    public boolean isAuthorizedUpload(String uploadUrl, String tenantId, String mimeType, long byteSize) {
        try {
            URI uri = URI.create(uploadUrl);
            String path = uri.getPath();
            String query = uri.getRawQuery();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && path != null
                    && path.contains("quiz-media/" + tenantId + "/")
                    && query != null
                    && query.toLowerCase(Locale.ROOT).contains("x-amz-signature=");
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
    }

    @Override
    public Optional<StoredObject> headObject(String storageKey) {
        try {
            var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            if (head.checksumSHA256() == null || head.checksumSHA256().isBlank()) return Optional.empty();
            String sha256 = HexFormat.of().formatHex(Base64.getDecoder().decode(head.checksumSHA256()));
            return Optional.of(new StoredObject(head.contentLength(), sha256, head.contentType()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw exception;
        }
    }

    private String checksumBase64(String storageKey) {
        String filename = storageKey.substring(storageKey.lastIndexOf('/') + 1);
        String sha256 = filename.substring(0, filename.indexOf('.'));
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256));
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> throw new IllegalArgumentException("unsupported media type");
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
