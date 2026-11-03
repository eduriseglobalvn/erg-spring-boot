package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class RuntimeDatabaseRoleGuardIntegrationTest {

    private static final String RUNTIME_PASSWORD = "guard_test_only";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_guard")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @BeforeAll
    static void createRoles() throws Exception {
        try (Connection owner = ownerConnection(); Statement sql = owner.createStatement()) {
            sql.execute("create table tenant_rows (id int primary key, tenant_id text not null)");
            sql.execute("create role guard_runtime login password '" + RUNTIME_PASSWORD + "'");
            sql.execute("grant select on tenant_rows to guard_runtime");
            sql.execute("create role guard_bypass login bypassrls password '" + RUNTIME_PASSWORD + "'");
            sql.execute("create role guard_owner login password '" + RUNTIME_PASSWORD + "'");
            sql.execute("create table owned_rows (id int primary key)");
            sql.execute("alter table owned_rows owner to guard_owner");
            sql.execute("create role guard_member login password '" + RUNTIME_PASSWORD + "'");
            sql.execute("grant guard_owner to guard_member");
        }
    }

    @Test
    void restrictedRuntimeRolePasses() throws Exception {
        try (Connection connection = connectionAs("guard_runtime")) {
            assertThat(RuntimeDatabaseRoleGuard.violations(connection)).isEmpty();
        }
    }

    @Test
    void superuserOwnerIsRejected() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(RuntimeDatabaseRoleGuard.violations(connection))
                    .anyMatch(v -> v.contains("SUPERUSER"))
                    .anyMatch(v -> v.contains("owns or inherits ownership"));
        }
    }

    @Test
    void bypassRlsRoleIsRejected() throws Exception {
        try (Connection connection = connectionAs("guard_bypass")) {
            assertThat(RuntimeDatabaseRoleGuard.violations(connection)).anyMatch(v -> v.contains("BYPASSRLS"));
        }
    }

    @Test
    void tableOwnerAndInheritedOwnerAreRejected() throws Exception {
        try (Connection connection = connectionAs("guard_owner")) {
            assertThat(RuntimeDatabaseRoleGuard.violations(connection)).containsExactly(
                    "role guard_owner owns or inherits ownership of 1 public tables");
        }
        try (Connection connection = connectionAs("guard_member")) {
            assertThat(RuntimeDatabaseRoleGuard.violations(connection))
                    .anyMatch(v -> v.contains("owns or inherits ownership of 1"));
        }
    }

    private static Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection connectionAs(String role) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), role, RUNTIME_PASSWORD);
    }
}
