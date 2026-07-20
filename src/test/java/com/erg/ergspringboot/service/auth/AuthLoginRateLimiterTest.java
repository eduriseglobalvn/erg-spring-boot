package com.erg.ergspringboot.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import com.erg.ergspringboot.platform.graphql.ratelimit.GraphQlRateLimitCounterRouter;

class AuthLoginRateLimiterTest {

    @Test
    void limitsOneSourceTryingManyAccountsWithoutPersistingRawIdentifiers() {
        AuthLoginRateLimiter limiter = AuthLoginRateLimiter.localForTest(2, 100, 100);
        AuthClientContext source = new AuthClientContext("203.0.113.10", "JUnit", "device-a", null, "fp-a");

        assertThat(limiter.allow("first@example.com", source).allowed()).isTrue();
        assertThat(limiter.allow("second@example.com", source).allowed()).isTrue();
        assertThat(limiter.allow("third@example.com", source).allowed()).isFalse();
        assertThat(limiter.debugKeys()).noneMatch(key -> key.contains("@") || key.contains("example.com"));
    }

    @Test
    void limitsOneAccountAcrossRotatingSourceAddresses() {
        AuthLoginRateLimiter limiter = AuthLoginRateLimiter.localForTest(100, 2, 100);

        assertThat(limiter.allow("victim@example.com", context("203.0.113.1", "a")).allowed()).isTrue();
        assertThat(limiter.allow("victim@example.com", context("203.0.113.2", "b")).allowed()).isTrue();
        assertThat(limiter.allow("victim@example.com", context("203.0.113.3", "c")).allowed()).isFalse();
    }

    @Test
    void redisFailureFailsClosedInsteadOfAllowingLoginOrReturningServerError() {
        @SuppressWarnings("unchecked")
        ObjectProvider<GraphQlRateLimitCounterRouter> provider = mock(ObjectProvider.class);
        GraphQlRateLimitCounterRouter counter = mock(GraphQlRateLimitCounterRouter.class);
        when(provider.getIfAvailable()).thenReturn(counter);
        when(counter.increment(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("redis unavailable"));
        AuthLoginRateLimiter limiter = AuthLoginRateLimiter.withCounterForTest(provider, 100, 100, 100);

        assertThat(limiter.allow("teacher@example.com", context("203.0.113.1", "a")).allowed()).isFalse();
    }

    private AuthClientContext context(String ip, String device) {
        return new AuthClientContext(ip, "JUnit", device, null, "fp-" + device);
    }
}
