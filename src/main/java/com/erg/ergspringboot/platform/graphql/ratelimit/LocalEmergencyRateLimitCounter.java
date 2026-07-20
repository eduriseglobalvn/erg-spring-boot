package com.erg.ergspringboot.platform.graphql.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public final class LocalEmergencyRateLimitCounter implements GraphQlRateLimitCounter {

    private final Cache<String, AtomicLong> counters = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterAccess(Duration.ofMinutes(1))
            .build();

    @Override
    public RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd) {
        AtomicLong counter = counters.get(key, ignored -> new AtomicLong());
        while (true) {
            long current = counter.get();
            if (current >= upperBound) {
                return new RateLimitCounterResult(current, false, windowEnd);
            }
            if (counter.compareAndSet(current, current + 1)) {
                return new RateLimitCounterResult(current + 1, true, windowEnd);
            }
        }
    }
}

