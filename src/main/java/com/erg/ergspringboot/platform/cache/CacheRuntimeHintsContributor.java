package com.erg.ergspringboot.platform.cache;

import org.springframework.aot.hint.RuntimeHints;

/** Module-owned Native reflection hints for cache value graphs and cache expressions. */
public interface CacheRuntimeHintsContributor {
    void registerHints(RuntimeHints hints, ClassLoader classLoader);
}
