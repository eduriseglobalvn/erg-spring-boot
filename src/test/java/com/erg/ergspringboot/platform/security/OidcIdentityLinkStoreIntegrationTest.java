package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.DockerClientFactory;

/** Proves V123 identity links persist under tenant RLS and refuse re-pointing an account. */
@ActiveProfiles("test")
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("postgresProofDatabaseAvailable")
class OidcIdentityLinkStoreIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void firstSubjectIsPinnedAndAnotherSubjectCannotTakeTheAccountOver() {
        String issuer = "https://auth.example.test/realms/erg";
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            cleanup();
            jdbc.update("insert into users (id, created_at, updated_at, email, tenant_id, status) "
                    + "values (?, now(), now(), ?, ?, ?)", "oidc-link-user", "oidc-link@example.test", "tenant-a", "ACTIVE");
        });
        OidcIdentityLinkStore store = new OidcIdentityLinkStore(jdbc, transactionManager);

        assertThat(store.linkedUserId("tenant-a", issuer, "sub-1")).isEmpty();
        assertThat(store.link("tenant-a", issuer, "sub-1", "oidc-link-user")).isTrue();
        assertThat(store.link("tenant-a", issuer, "sub-1", "oidc-link-user")).isTrue();
        assertThat(store.linkedUserId("tenant-a", issuer, "sub-1")).contains("oidc-link-user");
        assertThat(store.link("tenant-a", issuer, "sub-attacker", "oidc-link-user")).isFalse();
        assertThat(store.linkedUserId("tenant-b", issuer, "sub-1")).isEmpty();

        tx.executeWithoutResult(status -> cleanup());
    }

    private void cleanup() {
        jdbc.update("delete from user_identity_links where user_id = ?", "oidc-link-user");
        jdbc.update("delete from users where id = ?", "oidc-link-user");
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
