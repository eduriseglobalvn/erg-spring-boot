package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** STU-01: V143 clears the first-login flags only for accounts whose sole active role is the learner role. */
@Testcontainers(disabledWithoutDocker = true)
class StudentLifecycleBackfillMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_stu01")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @Test
    void clearsFlagsForStudentOnlyAccountsFromBothRoleTables() throws Exception {
        String url = postgres.getJdbcUrl();
        flyway(url).target(MigrationVersion.fromVersion("142")).load().migrate();
        try (Connection connection = connect(url); Statement statement = connection.createStatement()) {
            for (String id : new String[] {"u_legacy_stu", "u_assign_stu", "u_invited_stu", "u_stu_teacher",
                    "u_stu_legacy_teacher", "u_teacher", "u_stu_revoked_t"}) {
                statement.execute("insert into users (id, created_at, updated_at, email, full_name, status, provider,"
                        + " account_type, tenant_id, is_profile_completed, must_change_password,"
                        + " recovery_email_verified, login_count) values ('" + id + "', now(), now(), '" + id
                        + "@example.test', 'Flagged', '" + ("u_invited_stu".equals(id) ? "INVITED" : "ACTIVE")
                        + "', 'local', 'erg', 'erg', false, true, false, 0)");
            }
            statement.execute("insert into user_roles (user_id, role_id) values ('u_legacy_stu', 'role_lms_student'),"
                    + " ('u_stu_legacy_teacher', 'role_lms_student'), ('u_stu_legacy_teacher', 'role_lms_homeroom_teacher')");
            assign(statement, "u_assign_stu", "role_lms_student", false);
            assign(statement, "u_invited_stu", "role_lms_student", false);
            assign(statement, "u_stu_teacher", "role_lms_student", false);
            assign(statement, "u_stu_teacher", "role_lms_homeroom_teacher", false);
            assign(statement, "u_stu_legacy_teacher", "role_lms_student", false);
            assign(statement, "u_teacher", "role_lms_homeroom_teacher", false);
            assign(statement, "u_stu_revoked_t", "role_lms_student", false);
            assign(statement, "u_stu_revoked_t", "role_lms_homeroom_teacher", true);
        }

        assertThat(flyway(url).load().migrate().success).isTrue();

        try (Connection connection = connect(url); Statement statement = connection.createStatement()) {
            assertThat(cleared(statement)).containsExactly("u_assign_stu", "u_invited_stu", "u_legacy_stu", "u_stu_revoked_t");
            try (var result = statement.executeQuery("select status from users where id = 'u_invited_stu'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("ACTIVE");
            }
        }
    }

    private static java.util.List<String> cleared(Statement statement) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        try (var result = statement.executeQuery("select id from users where id like 'u\\_%' and is_profile_completed"
                + " and recovery_email_verified and not must_change_password order by id")) {
            while (result.next()) ids.add(result.getString(1));
        }
        return ids;
    }

    private static void assign(Statement statement, String userId, String roleId, boolean revoked) throws Exception {
        statement.execute("insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by,"
                + " revoked_at) values ('" + userId + (roleId.contains("teacher") ? "_t" : "_s") + "', '" + userId + "', '" + roleId
                + "', 'erg', 'TENANT', 'usr_super_admin', " + (revoked ? "now()" : "null") + ")");
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String url) {
        return Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK);
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }
}
