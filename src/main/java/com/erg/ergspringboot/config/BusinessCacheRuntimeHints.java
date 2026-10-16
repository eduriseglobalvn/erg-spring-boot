package com.erg.ergspringboot.config;

import com.erg.ergspringboot.platform.cache.CacheRuntimeHintsContributor;
import java.util.List;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/** Aggregates module-owned cache Native hints without importing business DTOs in platform cache code. */
public final class BusinessCacheRuntimeHints implements RuntimeHintsRegistrar {
    private final List<CacheRuntimeHintsContributor> contributors = List.of(
            new LmsCacheRuntimeHintsContributor(), new PlatformCacheRuntimeHintsContributor());

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        contributors.forEach(contributor -> contributor.registerHints(hints, classLoader));
    }
}
