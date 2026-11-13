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
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            rejectUnsafeLocalStoreInProd(localRoot, signingSecret, localAllowedInProd);
        }
        return new LocalPrivateObjectStorage(Path.of(localRoot), signingSecret, clock);
    }

    // A relative root (the default .erg-private) resolves inside the container's writable layer, which a
    // redeploy discards; an absolute ERG_TK_PHOTOS_LOCAL_ROOT is the explicit statement that a volume is
    // mounted there. A blank secret means a random per-process key, so signed URLs die on every restart.
    private static void rejectUnsafeLocalStoreInProd(String localRoot, String signingSecret, boolean localAllowedInProd) {
        if (!localAllowedInProd) {
            throw new IllegalStateException("Punch photos need the private R2 bucket in prod: "
                    + "set TK_PHOTOS_R2__ENABLED=true and TK_PHOTOS_R2__* (local disk storage is dev-only; "
                    + "TK_PHOTOS_LOCAL_ALLOWED_IN_PROD=true is a temporary opt-in)");
        }
        if (localRoot == null || localRoot.isBlank() || !Path.of(localRoot).isAbsolute()) {
            throw new IllegalStateException("TK_PHOTOS_LOCAL_ALLOWED_IN_PROD=true needs ERG_TK_PHOTOS_LOCAL_ROOT set to "
                    + "the absolute path of a mounted volume (included in backups); got '" + localRoot
                    + "', which lives in the container layer and is lost on redeploy");
        }
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException("SECRET_TK_PHOTO_URL_SIGNING_KEY must be set in prod: without it punch photo "
                    + "URLs are signed with a random per-process key and break on every restart");
        }
    }

    // The lcms media bucket is served publicly; selfies there would be world-readable.
    static void rejectPublicBucket(String bucket, String publicMediaBucket) {
        if (bucket != null && publicMediaBucket != null && !bucket.isBlank()
                && bucket.trim().equalsIgnoreCase(publicMediaBucket.trim())) {
            throw new IllegalStateException("erg.timekeeping.photos.r2.bucket-name must not be the public erg.r2.bucket-name");
        }
    }
}
