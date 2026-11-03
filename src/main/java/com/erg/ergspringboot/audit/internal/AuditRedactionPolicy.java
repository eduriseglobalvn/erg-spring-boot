package com.erg.ergspringboot.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Redacts secrets and pseudonymises common PII before the value reaches PostgreSQL.
 * Bounds are intentional: audit details must never become an unbounded payload
 * or a second copy of a request body.
 *
 * <p>Version 2 pseudonymises with HMAC-SHA256 under {@code erg.audit.redaction.hmac-secret}
 * (low-entropy values such as Vietnamese phone numbers cannot be reversed by brute force
 * without the key) and covers e-mail/name/birth-date/national-id/client-address keys.
 * The secret is mandatory in the {@code prod} profile (fail-closed at startup).</p>
 */
@Component
public class AuditRedactionPolicy {
    public static final int VERSION = 2;
    private static final Logger log = LoggerFactory.getLogger(AuditRedactionPolicy.class);
    private static final String DEV_ONLY_KEY = "erg-audit-redaction-dev-only-key";
    private static final int MAX_MAP_ENTRIES = 64;
    private static final int MAX_LIST_ENTRIES = 100;
    private static final int MAX_STRING_LENGTH = 2048;
    private static final Set<String> PII_EXACT_KEYS = Set.of(
            "ip", "clientip", "remoteip", "sourceip", "remoteaddr", "address",
            "name", "fullname", "firstname", "lastname", "displayname",
            "dob", "dateofbirth", "birthdate", "birthday",
            "nationalid", "cccd", "cmnd", "passport", "passportnumber");

    private final byte[] hmacKey;

    /** Tests and non-Spring embedders: development key, never valid for production data. */
    public AuditRedactionPolicy() {
        this(DEV_ONLY_KEY);
    }

    public AuditRedactionPolicy(String hmacSecret) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("audit redaction HMAC secret must not be blank");
        }
        this.hmacKey = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Autowired
    public AuditRedactionPolicy(
            @Value("${erg.audit.redaction.hmac-secret:${AUDIT_REDACTION_HMAC_SECRET:}}") String hmacSecret,
            Environment environment
    ) {
        this(resolveSecret(hmacSecret, environment));
    }

    private static String resolveSecret(String configured, Environment environment) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (environment != null && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "erg.audit.redaction.hmac-secret (AUDIT_REDACTION_HMAC_SECRET) is required in prod");
        }
        log.warn("erg.audit.redaction.hmac-secret is not configured; using a development-only key");
        return DEV_ONLY_KEY;
    }

    public Map<String, Object> redact(Map<String, Object> input) {
        Object value = sanitize(input == null ? Map.of() : input, 0, "details");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private Object sanitize(Object value, int depth, String key) {
        if (depth > 6) return "[DEPTH_LIMIT]";
        String normalizedKey = normalizeKey(key);
        if (isSecret(normalizedKey)) return "[REDACTED]";
        if (isPii(normalizedKey)) return hash(value);
        if (value == null || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof CharSequence text) return truncate(text.toString());
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_MAP_ENTRIES) break;
                String childKey = String.valueOf(entry.getKey());
                result.put(childKey, sanitize(entry.getValue(), depth + 1, childKey));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count++ >= MAX_LIST_ENTRIES) break;
                result.add(sanitize(item, depth + 1, key));
            }
            return result;
        }
        return truncate(String.valueOf(value));
    }

    private boolean isSecret(String key) {
        return key.contains("password") || key.contains("token") || key.contains("secret")
                || key.contains("otp") || key.contains("authorization") || key.contains("cookie")
                || key.contains("credential") || key.contains("privatekey") || key.contains("apikey")
                || key.contains("accesskey") || key.contains("refresh");
    }

    private boolean isPii(String key) {
        return key.contains("email") || key.contains("phone") || key.contains("mobile")
                || key.contains("ipaddress") || key.contains("useragent") || key.contains("remoteaddr")
                || key.contains("nationalid") || key.contains("dateofbirth")
                || PII_EXACT_KEYS.contains(key);
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private String truncate(String value) {
        return value.length() <= MAX_STRING_LENGTH
                ? value
                : value.substring(0, MAX_STRING_LENGTH) + "...[TRUNCATED]";
    }

    private String hash(Object value) {
        if (value == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Arrays.copyOf(hmacKey, hmacKey.length), "HmacSHA256"));
            byte[] digest = mac.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return "hmac-sha256:" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
