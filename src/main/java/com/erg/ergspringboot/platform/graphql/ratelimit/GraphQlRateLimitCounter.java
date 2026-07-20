package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.time.Instant;

public interface GraphQlRateLimitCounter {
    RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd);
}

