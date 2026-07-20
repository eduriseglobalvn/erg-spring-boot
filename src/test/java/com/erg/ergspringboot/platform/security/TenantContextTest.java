package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void defaultsBlankTenantToErgAndNullsBlankUserAndPortal() {
        TenantContext context = TenantContext.of("  ", "  ", "  ");

        assertThat(context.tenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        assertThat(context.userId()).isNull();
        assertThat(context.portal()).isNull();
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
}
