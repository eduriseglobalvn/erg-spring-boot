package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JacksonCacheValueCodecTest {

    record Dashboard(String studentId, int pendingAssignments) {
    }

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final JacksonCacheValueCodec codec = new JacksonCacheValueCodec(mapper);

    @Test
    void roundTripsTheDescriptorTypeWithoutPolymorphicMetadata() {
        CacheDescriptor<Dashboard> descriptor = descriptor(1024);
        Dashboard value = new Dashboard("student-1", 4);

        CacheEncoding encoded = codec.encode(descriptor, value);

        assertThat(encoded.cacheable()).isTrue();
        assertThat(encoded.serializedSize()).isEqualTo(encoded.bytes().length);
        assertThat(codec.decode(descriptor, encoded.bytes())).isEqualTo(value);
        assertThat(new String(encoded.bytes(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("@class");
    }

    @Test
    void bypassesValuesOverTheConfiguredByteLimit() {
        CacheDescriptor<Dashboard> descriptor = descriptor(32);

        CacheEncoding encoded = codec.encode(descriptor, new Dashboard("student-with-a-long-id", 400));

        assertThat(encoded.cacheable()).isFalse();
        assertThat(encoded.bytes()).isEmpty();
        assertThat(encoded.serializedSize()).isGreaterThan(32);
    }

    @Test
    void rejectsMalformedCachedJsonAsCorruption() {
        assertThatThrownBy(() -> codec.decode(descriptor(1024), "not-json".getBytes()))
                .isInstanceOf(CacheValueDecodingException.class);
    }

    private CacheDescriptor<Dashboard> descriptor(int maximumBytes) {
        return new CacheDescriptor<>("studentDashboard", mapper.constructType(Dashboard.class),
                Duration.ofSeconds(30), Duration.ofMinutes(2), 16 * 1024 * 1024L,
                maximumBytes, true, false);
    }
}
