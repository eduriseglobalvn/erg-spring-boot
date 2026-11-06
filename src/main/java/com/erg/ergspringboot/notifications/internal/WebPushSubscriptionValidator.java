package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Locale;

/**
 * Validates a browser PushSubscription before the server ever POSTs to its endpoint (SSRF-minded):
 * https only, no credentials, no localhost and no IP-literal host (push services always use DNS names).
 */
final class WebPushSubscriptionValidator {

    static final int MAX_ENDPOINT_LENGTH = 2048;
    static final int P256DH_BYTES = 65;
    static final int AUTH_BYTES = 16;

    private WebPushSubscriptionValidator() { }

    static String endpoint(String value) {
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
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !isPublicDnsName(host)) {
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

    private static boolean isPublicDnsName(String host) {
        if (host.isEmpty() || host.startsWith("[") || host.equals("localhost") || host.endsWith(".localhost")) {
            return false;
        }
        String[] labels = host.split("\\.");
        // An IPv4 literal (or decimal/hex form) never ends in a label starting with a letter; real TLDs do.
        String tld = labels[labels.length - 1];
        return labels.length >= 2 && !tld.isEmpty() && Character.isLetter(tld.charAt(0));
    }

    private static BadRequestException invalid(String field) {
        return new BadRequestException("Đăng ký Web Push không hợp lệ: " + field);
    }
}
