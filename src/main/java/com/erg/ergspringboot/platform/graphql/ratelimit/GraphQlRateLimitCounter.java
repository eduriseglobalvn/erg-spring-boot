package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.time.Instant;

public interface GraphQlRateLimitCounter {
    RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd);

    /** Current value of {@code key} without changing it; 0 when absent or expired. */
    long current(String key);
}

