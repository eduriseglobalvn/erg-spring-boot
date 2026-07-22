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

    private void assumeRuntimeRole(java.sql.Connection connection, String role)
            throws java.sql.SQLException {
        execute(connection, "create role " + role + " nologin");
        execute(connection, "grant select, insert, update on question_bank_subjects to " + role);
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
