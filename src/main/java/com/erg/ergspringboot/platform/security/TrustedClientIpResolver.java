package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/** Resolves security-sensitive client IPs without trusting caller-supplied proxy headers. */
@Component
public class TrustedClientIpResolver {
    private final List<IpAddressMatcher> trustedProxies;

    public TrustedClientIpResolver(
            @Value("${erg.http.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxies = Arrays.stream(value(trustedProxyCidrs).split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = literal(request == null ? null : request.getRemoteAddr());
        if (peer == null) return "unknown";
        if (!trusted(peer)) return peer;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer;
        List<String> chain = Arrays.stream(forwarded.split(",", -1)).map(String::trim).toList();
        List<String> addresses = chain.stream().map(this::literal).toList();
        if (addresses.stream().anyMatch(java.util.Objects::isNull)) return peer;
        for (int index = addresses.size() - 1; index >= 0; index--) {
            String candidate = addresses.get(index);
            if (!trusted(candidate)) return candidate;
        }
        return addresses.isEmpty() ? peer : addresses.getFirst();
    }

    private boolean trusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String literal(String input) {
        String candidate = value(input).trim();
        if (candidate.isEmpty() || !candidate.matches("[0-9A-Fa-f:.]+")) return null;
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
