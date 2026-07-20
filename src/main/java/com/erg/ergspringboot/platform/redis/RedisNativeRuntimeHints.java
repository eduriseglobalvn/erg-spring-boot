package com.erg.ergspringboot.platform.redis;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class RedisNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(
                ErgRedisProperties.class,
                MemberCategory.INVOKE_PUBLIC_METHODS
        );
        hints.reflection().registerType(
                ErgRedisProperties.Endpoint.class,
                MemberCategory.INVOKE_PUBLIC_METHODS
        );
    }
}
