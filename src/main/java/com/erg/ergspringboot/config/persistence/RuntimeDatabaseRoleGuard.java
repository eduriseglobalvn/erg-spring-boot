package com.erg.ergspringboot.config.persistence;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast check that the runtime datasource role cannot bypass tenant row-level security.
 *
 * <p>RLS (V29/V65/V75/V90/V111) only constrains a role that is not SUPERUSER, has no BYPASSRLS and
 * does not own (or inherit ownership of) the protected tables. Flyway runs as the owner through
 * {@code spring.flyway.user}; the application pool must use the restricted runtime role (for example
 * {@code erg_app}). In the {@code prod} profile a misconfigured role stops startup instead of silently
 * disabling tenant isolation.
 */
@Component
@DependsOn("postgresFlyway")
public class RuntimeDatabaseRoleGuard {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDatabaseRoleGuard.class);

    static final String ROLE_QUERY = """
            select r.rolname,
                   r.rolsuper,
                   r.rolbypassrls,
                   (select count(*)
                      from pg_class c
                      join pg_namespace n on n.oid = c.relnamespace
                     where n.nspname = 'public'
                       and c.relkind in ('r', 'p')
                       and pg_has_role(current_user, c.relowner, 'USAGE')) as owned_tables
            from pg_roles r
            where r.rolname = current_user
            """;

    private final DataSource dataSource;
    private final Environment environment;
    private final boolean enabled;

    // The prod profile is checked at runtime (not with @Profile) so the guard also survives AOT/native
    // builds, matching TrustedHeaderAuthProductionGuard.
    public RuntimeDatabaseRoleGuard(
            @Qualifier("postgresDataSource") DataSource dataSource,
            Environment environment,
            @Value("${erg.persistence.runtime-role-guard.enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.enabled = enabled;
    }

    @PostConstruct
    void verifyRuntimeRole() throws SQLException {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        if (!enabled) {
            log.warn("Runtime database role guard is DISABLED; tenant RLS may be bypassed by the runtime role");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            List<String> violations = violations(connection);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("Runtime database role must not bypass row-level security: "
                        + String.join("; ", violations)
                        + ". Connect the application as the restricted runtime role (e.g. erg_app) and run"
                        + " Flyway as the owner via SPRING_FLYWAY_USER.");
            }
        }
    }

    /** Returns human-readable violations for the role {@code connection} is authenticated as. */
    static List<String> violations(Connection connection) throws SQLException {
        List<String> violations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(ROLE_QUERY);
             ResultSet row = statement.executeQuery()) {
            if (!row.next()) {
                violations.add("current_user not found in pg_roles");
                return violations;
            }
            String role = row.getString("rolname");
            if (row.getBoolean("rolsuper")) {
                violations.add("role " + role + " is SUPERUSER");
            }
            if (row.getBoolean("rolbypassrls")) {
                violations.add("role " + role + " has BYPASSRLS");
            }
            long owned = row.getLong("owned_tables");
            if (owned > 0) {
                violations.add("role " + role + " owns or inherits ownership of " + owned + " public tables");
            }
        }
        return violations;
    }
}
