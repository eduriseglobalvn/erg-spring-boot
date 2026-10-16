package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsBlankTenantInsteadOfCreatingADefaultRequestContext() {
        assertThatThrownBy(() -> TenantContext.of("  ", "  ", "  "))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void trimsAndKeepsProvidedValues() {
        TenantContext context = TenantContext.of(" tenant-7 ", " user-9 ", " lcms ");

        assertThat(context.tenantId()).isEqualTo("tenant-7");
        assertThat(context.userId()).isEqualTo("user-9");
        assertThat(context.portal()).isEqualTo("lcms");
    }

    @Test
    void holderReturnsNullWhenUnset() {
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void holderRoundTripsAndClears() {
        TenantContext.set(TenantContext.of("tenant-1", "user-1", "lms"));

        TenantContext current = TenantContext.current();
        assertThat(current).isNotNull();
        assertThat(current.tenantId()).isEqualTo("tenant-1");
        assertThat(current.userId()).isEqualTo("user-1");
        assertThat(current.portal()).isEqualTo("lms");

        TenantContext.clear();
        assertThat(TenantContext.current()).isNull();
    }

    @Test
    void maintenanceContextIsExplicitlyMarked() {
        TenantContext context = TenantContext.maintenance();

        assertThat(context.isMaintenance()).isTrue();
        assertThat(context.tenantId()).isEqualTo("__maintenance__");
    }

    @Test
    void nestedScopeRestoresPreviousContextAndIsIdempotent() {
        TenantContext outer = TenantContext.of("outer", "user", "lms");
        TenantContext inner = TenantContext.of("inner", "worker", "lms");
        TenantContext.set(outer);

        TenantContext.Scope scope = TenantContext.scope(inner);
        assertThat(TenantContext.current()).isSameAs(inner);
        scope.close();
        scope.close();

        assertThat(TenantContext.current()).isSameAs(outer);
    }

    @Test
    void carrierContainsTenantMetadataButNoSecurityObject() {
        TenantContext original = TenantContext.of("tenant-a", "user-a", "teacher");
        TenantContext.set(original);

        TenantContext.Carrier carrier = TenantContext.capture();

        assertThat(carrier.tenantId()).isEqualTo("tenant-a");
        assertThat(carrier.userId()).isEqualTo("user-a");
        assertThat(carrier.portal()).isEqualTo("teacher");
        try (TenantContext.Scope ignored = TenantContext.install(carrier)) {
            assertThat(TenantContext.current().tenantId()).isEqualTo("tenant-a");
        }
        assertThat(TenantContext.current()).isSameAs(original);
    }
}
