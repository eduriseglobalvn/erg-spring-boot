package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PrometheusAccessRequestMatcherTest {

    @Test
    void deniesPrometheusByDefault() {
        PrometheusAccessRequestMatcher matcher = new PrometheusAccessRequestMatcher(false);

        assertThat(matcher.matches(request("GET", "/actuator/prometheus"))).isFalse();
    }

    @Test
    void enabledMatcherPermitsOnlyGetOnTheExactPrometheusEndpoint() {
        PrometheusAccessRequestMatcher matcher = new PrometheusAccessRequestMatcher(true);

        assertThat(matcher.matches(request("GET", "/actuator/prometheus"))).isTrue();
        assertThat(matcher.matches(request("POST", "/actuator/prometheus"))).isFalse();
        assertThat(matcher.matches(request("OPTIONS", "/actuator/prometheus"))).isFalse();
        assertThat(matcher.matches(request("GET", "/actuator"))).isFalse();
        assertThat(matcher.matches(request("GET", "/actuator/health"))).isFalse();
        assertThat(matcher.matches(request("GET", "/api/v1/users"))).isFalse();
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
