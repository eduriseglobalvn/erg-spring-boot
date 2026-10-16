package com.erg.ergspringboot.platform.cache;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/** Compatibility facade; ownership of DTO hints lives with module contributors in config. */
public final class BusinessCacheRuntimeHints implements RuntimeHintsRegistrar {
    private final RuntimeHintsRegistrar delegate = new com.erg.ergspringboot.config.BusinessCacheRuntimeHints();

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        delegate.registerHints(hints, classLoader);
    }
}
