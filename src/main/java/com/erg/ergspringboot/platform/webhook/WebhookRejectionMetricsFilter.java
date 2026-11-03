package com.erg.ergspringboot.platform.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records {@link WebhookInboxService#rejected} for failed bot webhook calls (C13). The
 * controller and exception handler keep producing the response; this filter only observes the
 * final status, so signature/secret failures (401/403), replays (409) and malformed bodies (400)
 * become bounded {@code webhook.rejected.*} counters.
 */
@Component
public class WebhookRejectionMetricsFilter extends OncePerRequestFilter {
    private final WebhookInboxService inbox;

    public WebhookRejectionMetricsFilter(WebhookInboxService inbox) {
        this.inbox = inbox;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return provider(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            String reason = reason(response.getStatus());
            if (reason != null) {
                inbox.rejected(provider(request), reason);
            }
        }
    }

    static String reason(int status) {
        return switch (status) {
            case 401, 403 -> "verification";
            case 409 -> "replay";
            case 400, 413, 415 -> "invalid";
            case 404 -> "disabled";
            default -> null;
        };
    }

    private static String provider(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/webhooks/discord".equals(path)) return "discord";
        if ("/webhooks/telegram".equals(path)) return "telegram";
        return null;
    }
}
