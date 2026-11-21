package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisCacheGenerationServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisCacheGenerationService generations = new RedisCacheGenerationService(redis);

    RedisCacheGenerationServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void failedBumpKeepsTheKeyUnknownUntilALaterBumpSucceeds() {
        when(values.get(org.mockito.ArgumentMatchers.anyString())).thenReturn("3");
        when(values.increment(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("redis down")).thenReturn(4L);
        assertThat(generations.current("t", "c")).isEqualTo(3);

        assertThat(generations.increment("t", "c")).isEqualTo(-1);
        assertThat(generations.current("t", "c")).isEqualTo(-1);

        assertThat(generations.increment("t", "c")).isEqualTo(4);
        assertThat(generations.current("t", "c")).isEqualTo(4);
    }
}
