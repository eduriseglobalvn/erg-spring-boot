package com.erg.ergspringboot.platform.graphql;

import com.erg.ergspringboot.platform.graphql.ratelimit.GraphQlRateLimitCounterRouter;
import com.erg.ergspringboot.platform.graphql.ratelimit.LocalEmergencyRateLimitCounter;
import com.erg.ergspringboot.platform.graphql.ratelimit.RateLimitCounterResult;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter for GraphQL requests.
 *
 * <p>Each unique (tenant, user, operation) triple gets its own bucket. Buckets
 * expire automatically after 60 seconds of inactivity, preventing unbounded
 * memory growth. The maximum number of buckets is capped at 20,000.</p>
 *
 * <p>The rate limit is enforced per wall-clock fixed window: the configured
 * requests-per-minute plus burst allowance. Requests exceeding the limit
 * receive a 429 response with a Retry-After header.</p>
 */
@Component
public class GraphQlRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final ErgGraphQlProperties properties;
    private final ObjectProvider<GraphQlRateLimitCounterRouter> redisCounterProvider;
    private final LocalEmergencyRateLimitCounter localCounter;

    public GraphQlRateLimiter(ErgGraphQlProperties properties) {
        this(properties, null, new LocalEmergencyRateLimitCounter());
    }

    @Autowired
    public GraphQlRateLimiter(
            ErgGraphQlProperties properties,
            ObjectProvider<GraphQlRateLimitCounterRouter> redisCounterProvider,
            LocalEmergencyRateLimitCounter localCounter
    ) {
        this.properties = properties;
        this.redisCounterProvider = redisCounterProvider;
        this.localCounter = localCounter;
    }

    public Decision allow(GraphQlRateLimitKey key) {
        ErgGraphQlProperties.RateLimit config = properties.getRateLimit();
        if (config == null || !config.isEnabled()) {
            return Decision.allow();
        }

        int limit = Math.max(1, config.getRequestsPerMinute()) + Math.max(0, config.getBurst());
        long now = System.currentTimeMillis();
        long windowStart = now - (now % WINDOW_MILLIS);

        if (config.isRedisEnabled() && redisCounterProvider != null
                && redisCounterProvider.getIfAvailable() != null) {
            return allowWithRedis(key, limit, windowStart, config);
        }
        long retryAfterMillis = Math.max(1, WINDOW_MILLIS - (now - windowStart));
        return allowLocal(key, limit, windowStart, retryAfterMillis);
    }

    private Decision allowWithRedis(
            GraphQlRateLimitKey key,
            int limit,
            long windowStart,
            ErgGraphQlProperties.RateLimit config
    ) {
        long now = System.currentTimeMillis();
        long retryAfterMillis = Math.max(1, WINDOW_MILLIS - (now - windowStart));
        String redisKey = redisKey(key, windowStart);
        Instant windowEnd = Instant.ofEpochMilli(windowStart + WINDOW_MILLIS);
        try {
            GraphQlRateLimitCounterRouter counter = redisCounterProvider.getIfAvailable();
            if (counter == null) {
                return allowLocal(key, limit, windowStart, retryAfterMillis);
            }
            RateLimitCounterResult result = counter.increment(redisKey, limit, windowEnd);
            return result.incrementApplied()
                    ? Decision.allow()
                    : Decision.reject((retryAfterMillis + 999) / 1000);
        } catch (RuntimeException ex) {
            if (config.isFailClosedOnRedisError()) {
                return Decision.reject(Math.max(1, (retryAfterMillis + 999) / 1000));
            }
            return allowLocal(key, limit, windowStart, retryAfterMillis);
        }
    }

    private Decision allowLocal(GraphQlRateLimitKey key, int limit, long windowStart, long retryAfterMillis) {
        RateLimitCounterResult result = localCounter.increment(
                redisKey(key, windowStart),
                limit,
                Instant.ofEpochMilli(windowStart + WINDOW_MILLIS));
        return result.incrementApplied()
                ? Decision.allow()
                : Decision.reject((retryAfterMillis + 999) / 1000);
    }

    private String redisKey(GraphQlRateLimitKey key, long windowStart) {
        return "erg:state:v1:graphql-rate-limit:" + digest(key) + ":" + windowStart;
    }

    private String digest(GraphQlRateLimitKey key) {
        String value = String.join("\u001f",
                safe(key.tenantId()), safe(key.userId()), safe(key.operationName()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision reject(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }

}
