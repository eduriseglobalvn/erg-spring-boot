package com.erg.ergspringboot.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class RedisNativeRuntimeHintsTest {

    @Test
    void registersConfigurationPropertiesForNativeBinding() {
        RuntimeHints hints = new RuntimeHints();

        new RedisNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                .onType(ErgRedisProperties.class)
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(ErgRedisProperties.Endpoint.class)
                .test(hints)).isTrue();
    }
}
