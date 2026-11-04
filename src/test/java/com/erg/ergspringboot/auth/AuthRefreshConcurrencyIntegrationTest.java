package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

/** Proves PostgreSQL refresh rotation locks serialize the same-token race. */
@ActiveProfiles("test")
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIf("postgresProofDatabaseAvailable")
class AuthRefreshConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private com.erg.ergspringboot.auth.AuthOperations auth;

    @Autowired
    private AuthTokenService tokens;

    @Test
    void secondRefreshLockWaitsThenSeesRotatedHash() throws Exception {
        String sessionId = "refresh-race-session";
        try (Connection setup = dataSource.getConnection()) {
            setup.setAutoCommit(false);
            execute(setup, "delete from user_sessions where id = ?", sessionId);
            execute(setup, "delete from users where id = ?", "refresh-race-user");
            execute(setup, "insert into users "
                    + "(id, created_at, updated_at, email, tenant_id, status) "
                    + "values (?, now(), now(), ?, ?, ?)",
                    "refresh-race-user", "refresh-race@example.test", "tenant-a", "ACTIVE");
            execute(setup, "insert into user_sessions "
                    + "(id, created_at, updated_at, user_id, session_id, refresh_token_hash, tenant_id, expires_at) "
                    + "values (?, now(), now(), ?, ?, ?, ?, now() + interval '1 day')",
                    sessionId, "refresh-race-user", "refresh-race", "old-hash", "tenant-a");
            setup.commit();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            assertThat(lock(first, "old-hash")).isTrue();
            execute(first, "update user_sessions set refresh_token_hash = ? where id = ?", "new-hash", sessionId);

            Future<Boolean> contender = executor.submit(() -> lock(second, "old-hash"));
            Thread.sleep(Duration.ofMillis(150));
            assertThat(contender).isNotDone();

            first.commit();
            assertThat(contender.get(3, TimeUnit.SECONDS)).isFalse();
            second.rollback();
        } finally {
            executor.shutdownNow();
        }

        try (Connection cleanup = dataSource.getConnection()) {
            execute(cleanup, "delete from user_sessions where id = ?", sessionId);
            execute(cleanup, "delete from users where id = ?", "refresh-race-user");
        }
    }

    @Test
    void replayedRefreshTokenRevocationAndSecurityEventSurviveTheRejectedRequest() throws Exception {
        String userId = "refresh-replay-user";
        String stolenToken = "refresh-replay-stolen-token";
        try (Connection setup = dataSource.getConnection()) {
            setup.setAutoCommit(false);
            execute(setup, "delete from security_events where user_id = ?", userId);
            execute(setup, "delete from user_sessions where user_id = ?", userId);
            execute(setup, "delete from users where id = ?", userId);
            execute(setup, "insert into users (id, created_at, updated_at, email, tenant_id, status) "
                    + "values (?, now(), now(), ?, ?, ?)", userId, "refresh-replay@example.test", "tenant-a", "ACTIVE");
            execute(setup, "insert into user_sessions (id, created_at, updated_at, user_id, session_id, "
                    + "refresh_token_hash, previous_refresh_token_hash, tenant_id, expires_at) "
                    + "values (?, now(), now(), ?, ?, ?, ?, ?, now() + interval '1 day')",
                    "refresh-replay-victim", userId, "refresh-replay-victim", "victim-current-hash",
                    tokens.hashToken(stolenToken), "tenant-a");
            execute(setup, "insert into user_sessions (id, created_at, updated_at, user_id, session_id, "
                    + "refresh_token_hash, tenant_id, expires_at) "
                    + "values (?, now(), now(), ?, ?, ?, ?, now() + interval '1 day')",
                    "refresh-replay-attacker", userId, "refresh-replay-attacker", "attacker-hash", "tenant-a");
            setup.commit();
        }

        assertThatThrownBy(() -> auth.refresh(
                new com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest(stolenToken, "device"),
                new com.erg.ergspringboot.common.dto.auth.AuthClientContext("203.0.113.5", "JUnit", "device", null, null)))
                .isInstanceOf(com.erg.ergspringboot.exception.auth.AuthFailure.InvalidToken.class);

        try (Connection check = dataSource.getConnection()) {
            assertThat(count(check, "select count(*) from user_sessions where user_id = ? "
                    + "and revoked_reason = 'refresh_token_reuse'", userId)).isEqualTo(2);
            assertThat(count(check, "select count(*) from security_events where user_id = ? "
                    + "and event_type = 'REFRESH_TOKEN_REUSE'", userId)).isEqualTo(1);
            execute(check, "delete from security_events where user_id = ?", userId);
            execute(check, "delete from user_sessions where user_id = ?", userId);
            execute(check, "delete from users where id = ?", userId);
        }
    }

    private long count(Connection connection, String sql, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private boolean lock(Connection connection, String hash) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from user_sessions where refresh_token_hash = ? for update")) {
            statement.setQueryTimeout(5);
            statement.setString(1, hash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void execute(Connection connection, String sql, String... values) throws Exception {
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
