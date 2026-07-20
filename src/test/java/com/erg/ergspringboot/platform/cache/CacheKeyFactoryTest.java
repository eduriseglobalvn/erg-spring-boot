package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CacheKeyFactoryTest {

    private final CacheKeyFactory keys = new CacheKeyFactory(JsonMapper.builder().build());
    private final CacheDescriptor<String> descriptor = new CacheDescriptor<>(
            "studentDashboard",
            JsonMapper.builder().build().constructType(String.class),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            16 * 1024 * 1024L,
            1024 * 1024,
            true,
            false
    );

    @Test
    void unorderedScopesProduceTheSameKey() {
        String first = keys.businessKey("tenant-a", descriptor, Set.of("class-b", "class-a"), 7);
        String second = keys.businessKey("tenant-a", descriptor, Set.of("class-a", "class-b"), 7);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("erg:cache:v2:{").contains(":studentDashboard:g7:");
    }

    @Test
    void tenantAndOrderedInputsRemainIsolated() {
        assertThat(keys.businessKey("tenant-a", descriptor, List.of("page-1", "page-2"), 0))
                .isNotEqualTo(keys.businessKey("tenant-b", descriptor, List.of("page-1", "page-2"), 0));
        assertThat(keys.businessKey("tenant-a", descriptor, List.of("page-1", "page-2"), 0))
                .isNotEqualTo(keys.businessKey("tenant-a", descriptor, List.of("page-2", "page-1"), 0));
    }

    @Test
    void requiredTenantCannotBeBlank() {
        assertThatThrownBy(() -> keys.businessKey(" ", descriptor, "42", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }
}

