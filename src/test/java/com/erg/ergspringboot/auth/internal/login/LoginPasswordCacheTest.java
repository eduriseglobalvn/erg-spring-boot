package com.erg.ergspringboot.auth.internal.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginPasswordCacheTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final Map<String, String> store = new HashMap<>();

    LoginPasswordCacheTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> store.get(call.<String>getArgument(0)));
        doAnswer(call -> store.put(call.getArgument(0), call.getArgument(1)))
                .when(values).set(anyString(), anyString(), eq(LoginPasswordCache.TTL));
    }

    @Test
    void recallsOnlyTheSamePasswordAgainstTheSameStoredHash() {
        LoginPasswordCache cache = new LoginPasswordCache(redis, "server-secret");
        cache.remember("u1", "hash-1", "secret");

        assertThat(cache.recalls("u1", "hash-1", "secret")).isTrue();
        assertThat(cache.recalls("u1", "hash-1", "wrong")).isFalse();
        assertThat(cache.recalls("u1", "hash-2", "secret")).as("password changed").isFalse();
        assertThat(cache.recalls("u2", "hash-1", "secret")).isFalse();
        // Redis never sees the password or the stored hash.
        assertThat(store.values()).allSatisfy(v -> assertThat(v).doesNotContain("secret").doesNotContain("hash-1"));
    }

    @Test
    void anotherServerKeyNeverRecalls() {
        new LoginPasswordCache(redis, "server-secret").remember("u1", "hash-1", "secret");
        assertThat(new LoginPasswordCache(redis, "rotated-secret").recalls("u1", "hash-1", "secret")).isFalse();
    }

    @Test
    void disabledWithoutAKeyAndRedisFailuresAreMisses() {
        LoginPasswordCache off = new LoginPasswordCache(redis, " ");
        off.remember("u1", "hash-1", "secret");
        assertThat(off.recalls("u1", "hash-1", "secret")).isFalse();
        verify(values, never()).set(anyString(), anyString(), eq(LoginPasswordCache.TTL));

        when(values.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        assertThat(new LoginPasswordCache(redis, "server-secret").recalls("u1", "hash-1", "secret")).isFalse();
    }
}
