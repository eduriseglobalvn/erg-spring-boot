package com.erg.ergspringboot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache("authPrincipal", Duration.ofSeconds(15), 10_000),
                cache("sessionBootstrap", Duration.ofSeconds(60), 2_000),
                cache("hoclieuLibraryBootstrap", Duration.ofSeconds(60), 2_000),
                cache("hoclieuStudioBootstrap", Duration.ofSeconds(60), 200),
                cache("hoclieuResources", Duration.ofSeconds(60), 2_000),
                cache("hoclieuResourceDetail", Duration.ofSeconds(60), 2_000),
                cache("lmsClassesBySchool", Duration.ofSeconds(60), 2_000),
                cache("organizationEducationUnits", Duration.ofSeconds(60), 500),
                cache("organizationEducationUnitEntities", Duration.ofSeconds(60), 500),
                cache("authorizationRoles", Duration.ofSeconds(60), 500),
                cache("authorizationPermissions", Duration.ofSeconds(60), 500)
        ));
        return manager;
    }

    private CaffeineCache cache(String name, Duration ttl, long maximumSize) {
        // Each hot read model gets its own TTL so auth revocation stays tighter than page bootstrap caching.
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .maximumSize(maximumSize)
                        .expireAfterWrite(ttl)
                        .build()
        );
    }
}
