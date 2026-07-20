package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CacheDescriptorRegistryTest {

    @Test
    void rejectsDuplicateCacheNames() {
        var mapper = JsonMapper.builder().build();
        CacheDescriptor<String> first = descriptor("dashboard", mapper.constructType(String.class));
        CacheDescriptor<String> duplicate = descriptor("dashboard", mapper.constructType(String.class));

        assertThatThrownBy(() -> new CacheDescriptorRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dashboard");
    }

    @Test
    void exposesOnlyExplicitlyRegisteredValueTypes() {
        var mapper = JsonMapper.builder().build();
        CacheDescriptor<String> descriptor = descriptor("dashboard", mapper.constructType(String.class));
        CacheDescriptorRegistry registry = new CacheDescriptorRegistry(List.of(descriptor));

        assertThat(registry.require("dashboard")).isSameAs(descriptor);
        assertThatThrownBy(() -> registry.require("jpaEntityCache"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CacheDescriptor<String> descriptor(String name, tools.jackson.databind.JavaType type) {
        return new CacheDescriptor<>(name, type, Duration.ofSeconds(10), Duration.ofMinutes(1),
                16 * 1024 * 1024L, 1024 * 1024, true, false);
    }
}

