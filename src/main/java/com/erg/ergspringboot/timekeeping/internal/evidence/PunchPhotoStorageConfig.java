package com.erg.ergspringboot.timekeeping.internal.evidence;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Private R2 bucket when {@code erg.timekeeping.photos.r2.enabled=true}, otherwise local disk. */
@Configuration(proxyBeanMethods = false)
class PunchPhotoStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "erg.timekeeping.photos.r2.enabled", havingValue = "true")
    R2PrivateObjectStorage timekeepingR2PhotoStorage(
            @Value("${erg.timekeeping.photos.r2.endpoint}") String endpoint,
            @Value("${erg.timekeeping.photos.r2.access-key-id}") String accessKey,
            @Value("${erg.timekeeping.photos.r2.secret-key}") String secretKey,
            @Value("${erg.timekeeping.photos.r2.region:auto}") String region,
            @Value("${erg.timekeeping.photos.r2.bucket-name}") String bucket,
            @Value("${erg.r2.bucket-name:}") String publicMediaBucket) {
        rejectPublicBucket(bucket, publicMediaBucket);
        return R2PrivateObjectStorage.create(endpoint, accessKey, secretKey, region, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(PrivateObjectStorage.class)
    LocalPrivateObjectStorage timekeepingLocalPhotoStorage(
            @Value("${erg.timekeeping.photos.local-root:.erg-private}") String localRoot,
            @Value("${erg.timekeeping.photos.signing-secret:}") String signingSecret,
            @Value("${erg.timekeeping.photos.local-allowed-in-prod:false}") boolean localAllowedInProd,
            Clock clock,
            Environment environment) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod") && !localAllowedInProd) {
            throw new IllegalStateException("Punch photos need the private R2 bucket in prod: "
                    + "set TK_PHOTOS_R2__ENABLED=true and TK_PHOTOS_R2__* (local disk storage is dev-only; "
                    + "TK_PHOTOS_LOCAL_ALLOWED_IN_PROD=true is a temporary opt-in)");
        }
        return new LocalPrivateObjectStorage(Path.of(localRoot), signingSecret, clock);
    }

    // The lcms media bucket is served publicly; selfies there would be world-readable.
    static void rejectPublicBucket(String bucket, String publicMediaBucket) {
        if (bucket != null && publicMediaBucket != null && !bucket.isBlank()
                && bucket.trim().equalsIgnoreCase(publicMediaBucket.trim())) {
            throw new IllegalStateException("erg.timekeeping.photos.r2.bucket-name must not be the public erg.r2.bucket-name");
        }
    }
}
