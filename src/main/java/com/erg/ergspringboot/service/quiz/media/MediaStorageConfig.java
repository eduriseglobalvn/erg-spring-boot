package com.erg.ergspringboot.service.quiz.media;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
public class MediaStorageConfig {

    // Without these, an unreachable R2 holds the request thread indefinitely (headObject runs
    // synchronously on the media-commit path). Bound the whole call and each network attempt.
    private static ClientOverrideConfiguration callTimeouts() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10))
                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "erg.r2.enabled", havingValue = "true")
    S3Client r2S3Client(
            @Value("${erg.r2.endpoint}") String endpoint,
            @Value("${erg.r2.access-key-id}") String accessKey,
            @Value("${erg.r2.secret-key}") String secretKey,
            @Value("${erg.r2.region:auto}") String region
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(callTimeouts())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "erg.r2.enabled", havingValue = "true")
    S3Presigner r2S3Presigner(
            @Value("${erg.r2.endpoint}") String endpoint,
            @Value("${erg.r2.access-key-id}") String accessKey,
            @Value("${erg.r2.secret-key}") String secretKey,
            @Value("${erg.r2.region:auto}") String region
    ) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        // Presigner only signs URLs locally (no network I/O), so it needs no call timeout.
    }

    @Bean
    @ConditionalOnProperty(name = "erg.r2.enabled", havingValue = "true")
    MediaStorageAdapter r2MediaStorageAdapter(
            S3Client r2S3Client,
            S3Presigner r2S3Presigner,
            @Value("${erg.r2.bucket-name}") String bucket,
            @Value("${erg.r2.public-domain}") String publicDomain
    ) {
        return new R2MediaStorageAdapter(r2S3Client, r2S3Presigner, bucket, publicDomain);
    }

    @Bean
    @ConditionalOnMissingBean(MediaStorageAdapter.class)
    MediaStorageAdapter localMediaStorageAdapter(
            org.springframework.core.env.Environment environment,
            @Value("${erg.quiz.media.public-base-url:/api/v1/lcms/media/blob}") String publicBaseUrl,
            @Value("${erg.quiz.media.upload-base-url:/api/v1/lcms/media/upload}") String uploadBaseUrl,
            @Value("${erg.quiz.media.signing-secret:${SECRET_MEDIA_URL_SIGNING_KEY:${SECRET_AUTH__JWT_SECRET:}}}") String signingSecret,
            @Value("${SECRET_AUTH__JWT_SECRET:}") String jwtSecret,
            @Value("${erg.quiz.media.local-root:.erg-media}") String localRoot
    ) {
        rejectMediaSecretReusingJwtSecret(environment, signingSecret, jwtSecret);
        return new LocalMediaStorageAdapter(publicBaseUrl, uploadBaseUrl, signingSecret,
                java.time.Clock.systemUTC(), Path.of(localRoot));
    }

    // In prod the media URL signing key must be its own secret. Reusing the JWT secret means one leak
    // compromises both auth tokens and media URLs; fail closed at startup. Dev/test keep the JWT
    // fallback so a local boot doesn't need a second secret configured.
    private static void rejectMediaSecretReusingJwtSecret(
            org.springframework.core.env.Environment environment, String signingSecret, String jwtSecret) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        if (!jwtSecret.isBlank() && jwtSecret.equals(signingSecret)) {
            throw new IllegalStateException(
                    "Media signing secret must not reuse the JWT secret in prod: set SECRET_MEDIA_URL_SIGNING_KEY");
        }
    }
}
