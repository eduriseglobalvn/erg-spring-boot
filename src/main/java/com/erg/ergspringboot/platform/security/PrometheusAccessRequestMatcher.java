package com.erg.ergspringboot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

@Component
final class PrometheusAccessRequestMatcher implements RequestMatcher {

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private final boolean publicEnabled;

    PrometheusAccessRequestMatcher(
            @Value("${erg.observability.prometheus-public-enabled:false}") boolean publicEnabled
    ) {
        this.publicEnabled = publicEnabled;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return publicEnabled
                && HttpMethod.GET.matches(request.getMethod())
                && PROMETHEUS_PATH.equals(request.getRequestURI());
    }
}
