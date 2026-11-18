package com.erg.ergspringboot.learningstandards.internal.storage;

import com.erg.ergspringboot.learningstandards.LearningStandardsArtifactStorage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Artifacts under {@code learning-standards/<tenantId>/<artifactKey>} in the PRIVATE R2 bucket: packages are not
 * public content, so they never go to the public media bucket of {@code erg.r2.*}. The private store is the one
 * configured for punch photos ({@code erg.timekeeping.photos.r2.*}, required in prod); this adapter reuses its
 * endpoint and key, and its bucket unless {@code erg.learning-standards.r2-bucket} names another private one.
 */
@Component
@ConditionalOnProperty(name = "erg.timekeeping.photos.r2.enabled", havingValue = "true")
public final class R2LearningStandardsArtifactStorage implements LearningStandardsArtifactStorage, AutoCloseable {
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
    private static final String PREFIX = "learning-standards/";
    private final S3Client client;
    private final String bucket;

    @Autowired
    R2LearningStandardsArtifactStorage(
            @Value("${erg.timekeeping.photos.r2.endpoint}") String endpoint,
            @Value("${erg.timekeeping.photos.r2.access-key-id}") String accessKey,
            @Value("${erg.timekeeping.photos.r2.secret-key}") String secretKey,
            @Value("${erg.timekeeping.photos.r2.region:auto}") String region,
            @Value("${erg.learning-standards.r2-bucket:${erg.timekeeping.photos.r2.bucket-name}}") String bucket,
            @Value("${erg.r2.bucket-name:}") String publicMediaBucket) {
        this(S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // Same bounds as the other R2 clients: an unreachable bucket must not hold a request forever.
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .build())
                .build(), bucket);
        if (this.bucket.equalsIgnoreCase(publicMediaBucket.trim())) {
            client.close();
            throw new IllegalStateException("learning-standard artifacts must not use the public erg.r2.bucket-name");
        }
    }

    public R2LearningStandardsArtifactStorage(S3Client client, String bucket) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("learning-standards bucket is required");
        this.client = client;
        this.bucket = bucket.trim();
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public Optional<ArtifactMetadata> head(String tenantId, String artifactKey) {
        if (!safeSegment(tenantId) || !safePath(artifactKey)) return Optional.empty();
        String key = PREFIX + tenantId + "/" + artifactKey;
        try {
            var head = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED).build());
            Long size = head.contentLength();
            if (size == null || size > MAX_ARTIFACT_BYTES) return Optional.empty();
            String checksum = head.checksumSHA256();
            // A value with "-N" is the composite checksum of a multipart upload, not the file's SHA-256.
            if (checksum != null && !checksum.isBlank() && !checksum.contains("-")) {
                return Optional.of(new ArtifactMetadata(size, HexFormat.of().formatHex(Base64.getDecoder().decode(checksum))));
            }
            // shortcut: an object uploaded without a SHA-256 checksum is downloaded to hash it, inside the
            // client's 10 s call timeout; upload large packages with --checksum-algorithm SHA256 (single part).
            try (var object = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
                return hash(object);
            }
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot inspect learning-standard artifact", exception);
        }
    }

    private static Optional<ArtifactMetadata> hash(InputStream input) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            size += read;
            if (size > MAX_ARTIFACT_BYTES) return Optional.empty();
            digest.update(buffer, 0, read);
        }
        return Optional.of(new ArtifactMetadata(size, HexFormat.of().formatHex(digest.digest())));
    }

    // Object keys are flat strings, but a key that climbs out of the tenant prefix must never be asked for.
    private static boolean safeSegment(String value) {
        return value != null && !value.isBlank() && !value.contains("/") && !value.contains("\\")
                && !value.equals(".") && !value.equals("..");
    }

    private static boolean safePath(String value) {
        if (value == null || value.isBlank() || value.contains("\\")) return false;
        for (String segment : value.split("/", -1)) {
            if (!safeSegment(segment)) return false;
        }
        return true;
    }
}
