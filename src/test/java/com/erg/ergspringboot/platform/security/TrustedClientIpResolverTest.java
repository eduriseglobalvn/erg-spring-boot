package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientIpResolverTest {

    @Test
    void ignoresSpoofedForwardingHeadersFromUntrustedPeer() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8,172.16.0.0/12");
        MockHttpServletRequest request = request("203.0.113.10", "1.1.1.1, 10.0.0.2");
        request.addHeader("X-Real-IP", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void walksTrustedProxyChainFromRightToLeft() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8,172.16.0.0/12");
        MockHttpServletRequest request = request("10.0.0.5", "198.51.100.24, 172.16.0.8, 10.0.0.4");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.24");
    }

    @Test
    void rejectsMalformedForwardedChainInsteadOfPartiallyTrustingIt() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = request("10.0.0.5", "198.51.100.24, not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void supportsIpv6TrustedProxyAndClient() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("2001:db8:abcd::/48");
        MockHttpServletRequest request = request("2001:db8:abcd::5", "2001:db8:ffff::10, 2001:db8:abcd::4");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8:ffff:0:0:0:0:10");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
