package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.time.Instant;

public record RateLimitCounterResult(long value, boolean incrementApplied, Instant expiresAt) {
}

