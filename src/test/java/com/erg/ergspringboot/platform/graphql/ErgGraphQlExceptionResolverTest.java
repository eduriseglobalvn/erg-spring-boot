package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.exception.BadRequestException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ErgGraphQlExceptionResolverTest {

    private final ErgGraphQlExceptionResolver resolver = new ErgGraphQlExceptionResolver();

    @Test
    void apiExceptionKeepsStableBusinessCodeAndStatus() {
        var descriptor = resolver.describe(new BadRequestException("invalid calendar range"), "req-1");

        assertThat(descriptor.code()).isEqualTo("BAD_REQUEST");
        assertThat(descriptor.message()).isEqualTo("invalid calendar range");
        assertThat(descriptor.status()).isEqualTo(400);
        assertThat(descriptor.internal()).isFalse();
    }

    @Test
    void accessDeniedUsesSameSafeContractAsRest() {
        var descriptor = resolver.describe(new AccessDeniedException("sensitive policy detail"), "req-2");

        assertThat(descriptor.code()).isEqualTo("ACCESS_DENIED");
        assertThat(descriptor.message()).isEqualTo("You do not have permission to access this resource");
        assertThat(descriptor.status()).isEqualTo(403);
    }

    @Test
    void unexpectedWrappedExceptionDoesNotLeakInternalMessage() {
        var descriptor = resolver.describe(
                new CompletionException(new IllegalStateException("database password leaked")), "req-3");

        assertThat(descriptor.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(descriptor.message()).isEqualTo("Unexpected server error");
        assertThat(descriptor.internal()).isTrue();
    }
}
