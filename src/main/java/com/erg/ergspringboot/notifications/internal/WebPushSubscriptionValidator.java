package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Validates a browser PushSubscription before the server ever POSTs to it (SSRF guard): https, port 443
 * or default, no userinfo, and a host on the configured push-service allowlist (label-boundary match, so
 * {@code evilnotify.windows.com} does not match {@code notify.windows.com}). Used at registration and
 * again at send time.
 */
public final class WebPushSubscriptionValidator {

    static final int MAX_ENDPOINT_LENGTH = 2048;
    static final int P256DH_BYTES = 65;
    static final int AUTH_BYTES = 16;

    private WebPushSubscriptionValidator() { }

    public static String endpoint(String value, List<String> allowedHostSuffixes) {
        String endpoint = value == null ? "" : value.trim();
        if (endpoint.isEmpty() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw invalid("endpoint");
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw invalid("endpoint");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean portOk = uri.getPort() == -1 || uri.getPort() == 443;
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !portOk
                || !allowedHost(host, allowedHostSuffixes)) {
            throw invalid("endpoint");
        }
        return endpoint;
    }

    /** Base64url key that must decode to exactly {@code bytes} bytes (p256dh: 65, auth: 16). */
    static String key(String value, int bytes, String name) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty() || key.length() > 128) throw invalid("keys." + name);
        try {
            if (Base64.getUrlDecoder().decode(key.replace("=", "")).length != bytes) throw invalid("keys." + name);
        } catch (IllegalArgumentException exception) {
            throw invalid("keys." + name);
        }
        return key;
    }

    private static boolean allowedHost(String host, List<String> suffixes) {
        if (host.isEmpty() || suffixes == null) return false;
        for (String configured : suffixes) {
            String suffix = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
            while (suffix.startsWith(".")) suffix = suffix.substring(1);
            if (!suffix.isEmpty() && (host.equals(suffix) || host.endsWith("." + suffix))) return true;
        }
        return false;
    }

    private static BadRequestException invalid(String field) {
        return new BadRequestException("Đăng ký Web Push không hợp lệ: " + field);
    }
}
