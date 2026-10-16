package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.platform.graphql.ratelimit.GraphQlRateLimitCounterRouter;
import com.erg.ergspringboot.platform.graphql.ratelimit.LocalEmergencyRateLimitCounter;
import com.erg.ergspringboot.platform.graphql.ratelimit.RateLimitCounterResult;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Multi-dimensional login limiter. Identifiers are SHA-256 hashed before entering counter keys. */
@Component
public class AuthLoginRateLimiter {
    private static final long WINDOW_MILLIS = 60_000L;
    private final ObjectProvider<GraphQlRateLimitCounterRouter> redisCounter;
    private final LocalEmergencyRateLimitCounter localCounter = new LocalEmergencyRateLimitCounter();
    private final int sourceLimit;
    private final int accountLimit;
    private final int deviceLimit;
    private final boolean captureDebugKeys;
    private final boolean enabled;
    private final Set<String> debugKeys = new LinkedHashSet<>();

    @Autowired
    public AuthLoginRateLimiter(
            ObjectProvider<GraphQlRateLimitCounterRouter> redisCounter,
            ErgAuthProperties properties) {
        this(redisCounter,
                properties.getLoginRateLimit().isEnabled(),
                properties.getLoginRateLimit().getSourcePerMinute(),
                properties.getLoginRateLimit().getAccountPerMinute(),
                properties.getLoginRateLimit().getDevicePerMinute(), false);
    }

    /** Compatibility constructor for focused unit tests and old embedders. */
    public AuthLoginRateLimiter(
            ObjectProvider<GraphQlRateLimitCounterRouter> redisCounter,
            boolean enabled,
            int sourceLimit,
            int accountLimit,
            int deviceLimit) {
        this(redisCounter, enabled, sourceLimit, accountLimit, deviceLimit, false);
    }

    private AuthLoginRateLimiter(ObjectProvider<GraphQlRateLimitCounterRouter> redisCounter,
            boolean enabled, int sourceLimit, int accountLimit, int deviceLimit, boolean captureDebugKeys) {
        this.redisCounter = redisCounter;
        this.enabled = enabled;
        this.sourceLimit = Math.max(1, sourceLimit);
        this.accountLimit = Math.max(1, accountLimit);
        this.deviceLimit = Math.max(1, deviceLimit);
        this.captureDebugKeys = captureDebugKeys;
    }

    static AuthLoginRateLimiter localForTest(int sourceLimit, int accountLimit, int deviceLimit) {
        return new AuthLoginRateLimiter(null, true, sourceLimit, accountLimit, deviceLimit, true);
    }

    static AuthLoginRateLimiter withCounterForTest(ObjectProvider<GraphQlRateLimitCounterRouter> provider,
            int sourceLimit, int accountLimit, int deviceLimit) {
        return new AuthLoginRateLimiter(provider, true, sourceLimit, accountLimit, deviceLimit, false);
    }

    public Decision allow(String identifier, AuthClientContext context) {
        if (!enabled) return new Decision(true, 0);
        long now = System.currentTimeMillis();
        long windowStart = now - now % WINDOW_MILLIS;
        Instant windowEnd = Instant.ofEpochMilli(windowStart + WINDOW_MILLIS);
        boolean source;
        boolean account;
        boolean device;
        try {
            source = increment(key("source", context == null ? null : context.ipAddress(), windowStart), sourceLimit, windowEnd);
            account = increment(key("account", identifier, windowStart), accountLimit, windowEnd);
            device = increment(key("device", context == null ? null
                    : first(context.deviceFingerprint(), context.deviceId()), windowStart), deviceLimit, windowEnd);
        } catch (RuntimeException unavailable) {
            return new Decision(false, Math.max(1, (windowEnd.toEpochMilli() - now + 999) / 1000));
        }
        long retryAfter = Math.max(1, (windowEnd.toEpochMilli() - now + 999) / 1000);
        return source && account && device ? new Decision(true, 0) : new Decision(false, retryAfter);
    }

    Set<String> debugKeys() { return Set.copyOf(debugKeys); }

    private boolean increment(String key, long limit, Instant windowEnd) {
        if (captureDebugKeys) debugKeys.add(key);
        GraphQlRateLimitCounterRouter redis = redisCounter == null ? null : redisCounter.getIfAvailable();
        RateLimitCounterResult result = redis == null
                ? localCounter.increment(key, limit, windowEnd)
                : redis.increment(key, limit, windowEnd);
        return result.incrementApplied();
    }

    private String key(String dimension, String value, long windowStart) {
        return "erg:state:v1:auth-login-rate-limit:" + dimension + ":" + digest(value) + ":" + windowStart;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(first(value, "unknown").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private String first(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {}
}
