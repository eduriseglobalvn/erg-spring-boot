package com.erg.ergspringboot.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Redacts secrets and hashes common PII before the value reaches PostgreSQL.
 * Bounds are intentional: audit details must never become an unbounded payload
 * or a second copy of a request body.
 */
@Component
public class AuditRedactionPolicy {
    public static final int VERSION = 1;
    private static final int MAX_MAP_ENTRIES = 64;
    private static final int MAX_LIST_ENTRIES = 100;
    private static final int MAX_STRING_LENGTH = 2048;

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
                || key.contains("credential") || key.contains("privatekey") || key.contains("api_key")
                || key.contains("accesskey") || key.contains("refresh");
    }

    private boolean isPii(String key) {
        return key.equals("email") || key.contains("phone") || key.contains("mobile")
                || key.equals("ip") || key.contains("ipaddress") || key.contains("useragent")
                || key.equals("address");
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
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("sha256:");
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
