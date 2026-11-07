package com.erg.ergspringboot.timekeeping.internal.evidence;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            @Value("${erg.timekeeping.photos.r2.bucket-name}") String bucket) {
        return R2PrivateObjectStorage.create(endpoint, accessKey, secretKey, region, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(PrivateObjectStorage.class)
    LocalPrivateObjectStorage timekeepingLocalPhotoStorage(
            @Value("${erg.timekeeping.photos.local-root:.erg-private}") String localRoot,
            @Value("${erg.timekeeping.photos.signing-secret:}") String signingSecret,
            Clock clock) {
        return new LocalPrivateObjectStorage(Path.of(localRoot), signingSecret, clock);
    }
}
