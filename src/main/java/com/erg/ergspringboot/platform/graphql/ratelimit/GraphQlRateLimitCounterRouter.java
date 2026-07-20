package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.time.Instant;

public final class GraphQlRateLimitCounterRouter implements GraphQlRateLimitCounter {

    private final RedisCapabilityDetector capabilities;
    private final Redis88IncrexRateLimitCounter increx;
    private final LuaFixedWindowRateLimitCounter lua;

    public GraphQlRateLimitCounterRouter(
            RedisCapabilityDetector capabilities,
            Redis88IncrexRateLimitCounter increx,
            LuaFixedWindowRateLimitCounter lua
    ) {
        this.capabilities = capabilities;
        this.increx = increx;
        this.lua = lua;
    }

    @Override
    public RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd) {
        return capabilities.supportsIncrex()
                ? increx.increment(key, upperBound, windowEnd)
                : lua.increment(key, upperBound, windowEnd);
    }
}

