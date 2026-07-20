package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class SchoolCacheRegistrationTest {
    @Test
    void registersSchoolDirectoryCacheUsedByReadAndCommandServices() {
        CacheManager cacheManager = new CacheConfig().cacheManager();

        assertThat(cacheManager.getCache("lcmsSchoolDirectory")).isNotNull();
    }
}
