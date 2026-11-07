package com.erg.ergspringboot.timekeeping.internal.evidence;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Punch photos in their own private R2/S3 bucket. Deliberately separate from the lcms media bucket,
 * which is served publicly; this one has no public domain and is read only through presigned GETs.
 */
final class R2PrivateObjectStorage implements PrivateObjectStorage, AutoCloseable {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    R2PrivateObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("timekeeping photo bucket is required");
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket.trim();
    }

    static R2PrivateObjectStorage create(String endpoint, String accessKey, String secretKey, String region, String bucket) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        // Same bounds as the lcms R2 client: an unreachable bucket must not hold a punch request or the purge job forever.
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(pathStyle)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(pathStyle)
                .build();
        return new R2PrivateObjectStorage(client, presigner, bucket);
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        try {
            return presigner.presignGetObject(request).url().toURI();
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException("presigned photo URL is not a valid URI", invalid);
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
