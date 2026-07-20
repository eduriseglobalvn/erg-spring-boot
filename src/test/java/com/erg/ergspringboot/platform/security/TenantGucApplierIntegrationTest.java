package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Proves the Phase 0.4 RLS groundwork: {@link TenantGucApplier} writes app.tenant_id/app.user_id/
 * app.portal as transaction-local GUCs that current_setting(...) reads back. RLS is not enforced yet;
 * this only verifies the plumbing that Phase 6 will switch on.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class TenantGucApplierIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Test
    void appliesTenantUserPortalAsTransactionLocalGucs() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            TenantGucApplier.apply(connection, TenantContext.of("tenant-guc", "user-guc", "lcms"));

            assertThat(readGuc(connection, TenantGucApplier.TENANT_GUC)).isEqualTo("tenant-guc");
            assertThat(readGuc(connection, TenantGucApplier.USER_GUC)).isEqualTo("user-guc");
            assertThat(readGuc(connection, TenantGucApplier.PORTAL_GUC)).isEqualTo("lcms");
        });
    }

    @Test
    void readingUnsetGucDoesNotErrorWhenMissingFlagUsed() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            String value = readGuc(connection, "app.never_set");
            assertThat(value).isEmpty();
        });
    }

    private String readGuc(java.sql.Connection connection, String key) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select current_setting(?, true)")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                String value = resultSet.getString(1);
                return value == null ? "" : value;
            }
        }
    }

    static boolean postgresProofDatabaseAvailable() {
        if (firstText(
                System.getProperty("erg.test.postgres.url"),
                System.getenv("ERG_TEST_POSTGRES_URL")
        ) != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
