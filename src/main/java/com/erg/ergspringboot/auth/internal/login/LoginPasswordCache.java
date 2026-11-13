package com.erg.ergspringboot.auth.internal.login;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Remembers, for 12 hours, that a user's current password was verified, so a lab of students logging in again the
 * same day skips Argon2. Redis holds only HMAC(server key, user id, stored hash, password): useless without the key,
 * and a changed password (new stored hash) never matches. A wrong password always misses and pays the full hash.
 * Redis trouble is a miss, never a failure.
 */
@Component
class LoginPasswordCache {

    private static final Logger log = LoggerFactory.getLogger(LoginPasswordCache.class);
    static final Duration TTL = Duration.ofHours(12);
    private static final String PREFIX = "erg:auth:login-verified:";

    private final StringRedisTemplate redis;
    private final SecretKeySpec key;

    @Autowired
    LoginPasswordCache(@Qualifier("cacheStringRedisTemplate") ObjectProvider<StringRedisTemplate> redis,
            @Value("${SECRET_AUTH_LOGIN_CACHE_KEY:}") String serverSecret) {
        this(redis.getIfAvailable(), serverSecret);
    }

    /** Off (always a miss) without the cache Redis or a server secret. */
    LoginPasswordCache(StringRedisTemplate redis, String serverSecret) {
        this.redis = redis;
        this.key = redis == null || serverSecret == null || serverSecret.isBlank() ? null : derive(serverSecret);
    }

    boolean recalls(String userId, String storedHash, String rawPassword) {
        if (key == null || userId == null || storedHash == null || rawPassword == null) return false;
        try {
            String remembered = redis.opsForValue().get(PREFIX + userId);
            return remembered != null && MessageDigest.isEqual(
                    remembered.getBytes(StandardCharsets.US_ASCII),
                    proof(userId, storedHash, rawPassword).getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException ex) {
            log.debug("login password cache read failed: {}", ex.toString());
            return false;
        }
    }

    void remember(String userId, String storedHash, String rawPassword) {
        if (key == null || userId == null || storedHash == null || rawPassword == null) return;
        try {
            redis.opsForValue().set(PREFIX + userId, proof(userId, storedHash, rawPassword), TTL);
        } catch (RuntimeException ex) {
            log.debug("login password cache write failed: {}", ex.toString());
        }
    }

    private String proof(String userId, String storedHash, String rawPassword) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(userId.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(storedHash.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static SecretKeySpec derive(String serverSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serverSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretKeySpec(mac.doFinal("erg-login-password-cache-v1".getBytes(StandardCharsets.US_ASCII)), "HmacSHA256");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
