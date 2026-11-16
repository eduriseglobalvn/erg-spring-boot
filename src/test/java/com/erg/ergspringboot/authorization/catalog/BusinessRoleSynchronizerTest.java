package com.erg.ergspringboot.authorization.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.config.persistence.FlywayConfig;
import com.erg.ergspringboot.platform.security.PrincipalCache;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class BusinessRoleSynchronizerTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_rbac")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private static Set<String> permissionsOf(String roleId) {
        return new HashSet<>(jdbc.queryForList("""
                select p.name from role_permissions rp join permissions p on p.id = rp.permission_id
                where rp.role_id = ?
                """, String.class, roleId));
    }

    @Test
    void syncUpsertsRolesAndPermissions() {
        BusinessRoleSynchronizer sync = new BusinessRoleSynchronizer(jdbc, mock(PrincipalCache.class), false);

        sync.sync();

        for (BusinessRole role : BusinessRoleCatalog.all()) {
            assertThat(jdbc.queryForObject("select name from roles where id = ?", String.class, role.id()))
                    .isEqualTo(role.code());
            assertThat(permissionsOf(role.id())).as(role.id()).isEqualTo(role.permissions());
        }
        // Idempotent: a second run changes nothing.
        assertThat(sync.sync()).isFalse();
    }

    @Test
    void syncFailsOnUnknownRoleInDb() {
        jdbc.update("insert into roles (id, created_at, updated_at, name) values ('role_legacy_x', now(), now(), 'legacy.x')");
        try {
            BusinessRoleSynchronizer strict = new BusinessRoleSynchronizer(jdbc, mock(PrincipalCache.class), true);
            assertThatThrownBy(strict::sync).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("role_legacy_x");
            // Without the strict flag the foreign role is only logged.
            new BusinessRoleSynchronizer(jdbc, mock(PrincipalCache.class), false).sync();
        } finally {
            jdbc.update("delete from roles where id = 'role_legacy_x'");
        }
    }

    @Test
    void syncEvictsPrincipalCache() {
        PrincipalCache cache = mock(PrincipalCache.class);
        BusinessRoleSynchronizer sync = new BusinessRoleSynchronizer(jdbc, cache, false);
        sync.sync();
        org.mockito.Mockito.clearInvocations(cache);

        jdbc.update("delete from role_permissions where role_id = 'role_lms_student'");
        assertThat(sync.sync()).isTrue();
        verify(cache, times(1)).evictAll();

        org.mockito.Mockito.clearInvocations(cache);
        assertThat(sync.sync()).isFalse();
        verify(cache, never()).evictAll();
        assertThat(List.copyOf(permissionsOf("role_lms_student")))
                .containsExactlyInAnyOrderElementsOf(BusinessRoleCatalog.all().stream()
                        .filter(r -> r.id().equals("role_lms_student")).findFirst().orElseThrow().permissions());
    }
}
