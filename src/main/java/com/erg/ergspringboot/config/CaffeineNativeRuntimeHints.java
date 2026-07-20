package com.erg.ergspringboot.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public class CaffeineNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerCaffeineImplementation(hints, "com.github.benmanes.caffeine.cache.SSMSA");
        registerCaffeineImplementation(hints, "com.github.benmanes.caffeine.cache.SSSMWW");
        registerCaffeineNodeFactory(hints, "com.github.benmanes.caffeine.cache.PSAMS");
        registerCaffeineNodeFactory(hints, "com.github.benmanes.caffeine.cache.PSWMW");
    }

    private void registerCaffeineImplementation(RuntimeHints hints, String className) {
        hints.reflection().registerType(
                TypeReference.of(className),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.ACCESS_DECLARED_FIELDS
        );
    }

    private void registerCaffeineNodeFactory(RuntimeHints hints, String className) {
        hints.reflection().registerType(
                TypeReference.of(className),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        );
    }
}
