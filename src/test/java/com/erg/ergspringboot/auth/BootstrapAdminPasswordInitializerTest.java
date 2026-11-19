package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class BootstrapAdminPasswordInitializerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordService passwords = new PasswordService(new ErgAuthProperties());

    @Test
    void setsTheConfiguredPasswordWhileTheAdminStillCarriesTheMigrationHash() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH)))
                .thenReturn(1);
        when(jdbc.update(anyString(), anyString(), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH))).thenReturn(1);

        assertThat(new BootstrapAdminPasswordInitializer(jdbc, passwords, "first-Pass1").apply()).isTrue();

        // Production runs as a role without BYPASSRLS: the admin row is visible only in maintenance mode.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(jdbc);
        order.verify(jdbc).queryForObject("select set_config('app.maintenance_mode', 'true', true)", String.class);
        order.verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), hash.capture(), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH));
        assertThat(passwords.matches("first-Pass1", hash.getValue())).isTrue();
        // Only the untouched admin row, and it must change the password at first login.
        assertThat(sql.getValue()).contains("must_change_password = true")
                .contains("id = 'usr_super_admin' and password_hash = ?");
    }

    /** ENV-002: no password in the code; without the variable the admin is not given one. */
    @Test
    void withoutTheVariableTheAdminGetsNoPassword() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH)))
                .thenReturn(1);

        assertThat(new BootstrapAdminPasswordInitializer(jdbc, passwords, "").apply()).isFalse();

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        org.springframework.beans.factory.annotation.Value value = BootstrapAdminPasswordInitializer.class.getConstructors()[0]
                .getParameters()[2].getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        assertThat(value.value()).isEqualTo("${erg.bootstrap.admin-password:}");
    }

    @Test
    void neverTouchesAnAdminPasswordThatWasChanged() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(BootstrapAdminPasswordInitializer.MIGRATION_HASH)))
                .thenReturn(0);

        assertThat(new BootstrapAdminPasswordInitializer(jdbc, passwords, "first-Pass1").apply()).isFalse();

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
