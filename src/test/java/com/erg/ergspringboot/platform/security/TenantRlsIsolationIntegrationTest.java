package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** PostgreSQL proof that strict quiz-domain RLS denies cross-tenant reads and writes. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@org.junit.jupiter.api.condition.EnabledIf("postgresProofDatabaseAvailable")
class TenantRlsIsolationIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Test
    void questionBankRowsAreVisibleOnlyToTheCurrentTenant() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            TenantGucApplier.apply(connection, TenantContext.maintenance());
            insertSubject(connection, "rls-a", "tenant-a");
            insertSubject(connection, "rls-b", "tenant-b");
            assumeRuntimeRole(connection, "erg_rls_reader");

            TenantGucApplier.apply(connection, TenantContext.of("tenant-a", "user-a", "lcms"));
            assertThat(count(connection)).isEqualTo(1);

            TenantGucApplier.apply(connection, TenantContext.of("tenant-b", "user-b", "lcms"));
            assertThat(count(connection)).isEqualTo(1);
        });
    }

    @Test
    void currentTenantCannotInsertAnotherTenantsRow() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            assumeRuntimeRole(connection, "erg_rls_writer");
            TenantGucApplier.apply(connection, TenantContext.of("tenant-a", "user-a", "lcms"));

            assertThatThrownBy(() -> insertSubject(connection, "rls-cross", "tenant-b"))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("row-level security");
        });
    }

    @Test
    void legacyUserTableIsIsolatedForANonOwnerRuntimeRole() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            TenantGucApplier.apply(connection, TenantContext.maintenance());
            insertUser(connection, "rls-user-a", "tenant-a");
            insertUser(connection, "rls-user-b", "tenant-b");
            assumeRuntimeRole(connection, "erg_rls_legacy_users", "users");

            TenantGucApplier.apply(connection, TenantContext.of("tenant-a", "user-a", "admin"));
            assertThat(countUsers(connection)).isEqualTo(1);
            assertThatThrownBy(() -> insertUser(connection, "rls-user-cross", "tenant-b"))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("row-level security");
        });
    }

    @Test
    void bearerSessionLookupUsesTenantRlsForANonOwnerRuntimeRole() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            TenantGucApplier.apply(connection, TenantContext.maintenance());
            insertUser(connection, "rls-auth-user-a", "tenant-a");
            insertSession(connection, "rls-auth-session-a", "rls-auth-user-a", "tenant-a", "token-hash-a");
            assumeRuntimeRole(connection, "erg_rls_auth_session", "user_sessions");
            assertConstrainedRuntimeRole(connection, "erg_rls_auth_session", "user_sessions");

            TenantGucApplier.apply(connection, TenantContext.of("tenant-a", null, "lms"));
            assertThat(readGuc(connection, TenantGucApplier.MAINTENANCE_GUC)).isEqualTo("false");
            assertThat(countSessions(connection, "token-hash-a", "tenant-a")).isEqualTo(1);

            TenantGucApplier.apply(connection, TenantContext.of("tenant-b", null, "lms"));
            assertThat(countSessions(connection, "token-hash-a", "tenant-a")).isZero();
        });
    }

    private long count(java.sql.Connection connection) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from question_bank_subjects")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void insertSubject(java.sql.Connection connection, String id, String tenantId)
            throws java.sql.SQLException {
        execute(connection, "insert into question_bank_subjects "
                + "(id, created_at, updated_at, tenant_id, label) values (?, now(), now(), ?, ?)",
                id, tenantId, id);
    }

    private long countUsers(java.sql.Connection connection) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from users where id in ('rls-user-a', 'rls-user-b')")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void insertUser(java.sql.Connection connection, String id, String tenantId)
            throws java.sql.SQLException {
        execute(connection, "insert into users "
                        + "(id, created_at, updated_at, email, tenant_id, status) "
                        + "values (?, now(), now(), ?, ?, 'ACTIVE')",
                id, id + "@example.test", tenantId);
    }

    private void insertSession(
            java.sql.Connection connection,
            String id,
            String userId,
            String tenantId,
            String accessTokenHash
    ) throws java.sql.SQLException {
        execute(connection, "insert into user_sessions "
                        + "(id, created_at, updated_at, user_id, session_id, access_token_hash, tenant_id, expires_at) "
                        + "values (?, now(), now(), ?, ?, ?, ?, now() + interval '1 hour')",
                id, userId, id, accessTokenHash, tenantId);
    }

    private long countSessions(
            java.sql.Connection connection,
            String accessTokenHash,
            String tenantId
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from user_sessions where access_token_hash = ? and tenant_id = ?")) {
            statement.setString(1, accessTokenHash);
            statement.setString(2, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void assertConstrainedRuntimeRole(
            java.sql.Connection connection,
            String role,
            String table
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select current_user, roles.rolbypassrls, pg_get_userbyid(tables.relowner) = current_user
                from pg_roles roles
                cross join pg_class tables
                join pg_namespace namespace on namespace.oid = tables.relnamespace
                where roles.rolname = ? and namespace.nspname = 'public' and tables.relname = ?
                """)) {
            statement.setString(1, role);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(role);
                assertThat(resultSet.getBoolean(2)).as("runtime role must not BYPASSRLS").isFalse();
                assertThat(resultSet.getBoolean(3)).as("runtime role must not own the protected table").isFalse();
            }
        }
    }

    private String readGuc(java.sql.Connection connection, String key) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select current_setting(?, true)")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private void assumeRuntimeRole(java.sql.Connection connection, String role)
            throws java.sql.SQLException {
        assumeRuntimeRole(connection, role, "question_bank_subjects");
    }

    private void assumeRuntimeRole(java.sql.Connection connection, String role, String table)
            throws java.sql.SQLException {
        execute(connection, "create role " + role + " nologin");
        execute(connection, "grant select, insert, update on " + table + " to " + role);
        execute(connection, "set local role " + role);
    }

    private void execute(java.sql.Connection connection, String sql, String... values)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    static boolean postgresProofDatabaseAvailable() {
        if (System.getProperty("erg.test.postgres.url") != null
                || System.getenv("ERG_TEST_POSTGRES_URL") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
